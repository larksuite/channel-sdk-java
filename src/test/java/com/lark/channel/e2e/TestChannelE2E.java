// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel.e2e;

import com.lark.channel.ChannelEventHandler;
import com.lark.channel.LarkChannel;
import com.lark.channel.LarkChannelFactory;
import com.lark.channel.config.LarkChannelOptions;
import com.lark.channel.model.BotIdentity;
import com.lark.channel.model.CardActionEvent;
import com.lark.channel.model.CardStreamController;
import com.lark.channel.model.CardStreamProducer;
import com.lark.channel.model.ChatInfo;
import com.lark.channel.model.CommentEvent;
import com.lark.channel.model.MarkdownStreamController;
import com.lark.channel.model.MarkdownStreamProducer;
import com.lark.channel.model.NormalizedMessage;
import com.lark.channel.model.ReactionEvent;
import com.lark.channel.model.RejectEvent;
import com.lark.channel.model.SendInput;
import com.lark.channel.model.SendOptions;
import com.lark.channel.model.SendResult;
import com.lark.channel.model.StreamInput;
import com.lark.oapi.service.im.v1.model.CreateFileReq;
import com.lark.oapi.service.im.v1.model.CreateFileReqBody;
import com.lark.oapi.service.im.v1.model.CreateFileResp;
import com.lark.oapi.service.im.v1.model.CreateImageReq;
import com.lark.oapi.service.im.v1.model.CreateImageReqBody;
import com.lark.oapi.service.im.v1.model.CreateImageResp;
import com.lark.oapi.service.im.v1.model.ListMessageReq;
import com.lark.oapi.service.im.v1.model.ListMessageResp;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Credentialed Channel SDK end-to-end tests.
 *
 * <p>This class never contacts a live tenant unless the process environment
 * explicitly contains {@code LARK_CHANNEL_E2E_ENABLED=true}. Runtime
 * configuration is read from a Git-ignored {@code .env} file at the repository
 * root and is intentionally not printed by this test.
 */
public class TestChannelE2E {
    private static final String ENABLED = "LARK_CHANNEL_E2E_ENABLED";
    private static final String DRY_RUN = "LARK_CHANNEL_E2E_DRY_RUN";
    private static final String MANUAL = "LARK_CHANNEL_E2E_MANUAL";
    private static final String ENABLE_POLICY = "LARK_CHANNEL_E2E_ENABLE_POLICY";
    private static final String ENABLE_BOT_ADDED = "LARK_CHANNEL_E2E_ENABLE_BOT_ADDED";
    private static final String FOCUSED_COMMENT = "LARK_CHANNEL_E2E_FOCUSED_COMMENT";

    @Test
    public void testChannelE2E() throws Exception {
        Assume.assumeTrue("Set LARK_CHANNEL_E2E_ENABLED=true to run credentialed Channel E2E tests.",
                isTrue(System.getenv(ENABLED)));

        Path root = findRepositoryRoot();
        E2EConfig config = E2EConfig.load(root, System.getenv());
        List<String> validationErrors = config.validate();
        E2EReport report = new E2EReport(root, config, randomTrace());
        report.addEnvironmentCheck("configuration", validationErrors.isEmpty(),
                validationErrors.isEmpty() ? "configuration is valid" : join(validationErrors, "; "));

        if (!validationErrors.isEmpty()) {
            report.write();
            Assert.fail("Invalid Channel E2E configuration: " + join(validationErrors, "; "));
        }

        E2EPlan plan = E2EPlan.create(config);
        report.recordPlan(plan);
        if (isTrue(System.getenv(DRY_RUN))) {
            report.addEnvironmentCheck("dry_run", true, "validated configuration and case plan without live API calls");
            report.write();
            return;
        }

        LarkChannel channel = null;
        EventTracker events = new EventTracker();
        try {
            channel = createChannel(config, isTrue(System.getenv(ENABLE_POLICY)));
            registerEventHandlers(channel, config, report.getTrace(), events);
            final LarkChannel activeChannel = channel;

            runCase(report, plan, "lifecycle.connect", new CheckedAction() {
                @Override
                public void run() throws Exception {
                    BotIdentity identity = await(activeChannel.connect(), config.getConnectTimeoutSeconds());
                    requireText(identity == null ? null : identity.getOpenId(), "bot identity is empty");
                }
            }, config);

            if (!report.isPassed("lifecycle.connect")) {
                report.markBlockedRemainingAutomaticCases("connection did not complete");
            } else if (isTrue(System.getenv(FOCUSED_COMMENT))) {
                runFocusedCommentCase(config, plan, report, events);
            } else {
                runAutomaticCases(activeChannel, config, plan, report);
                runManualCasesWhenEnabled(activeChannel, config, plan, report, events);
            }
        } finally {
            if (channel != null) {
                try {
                    await(channel.disconnect(), config.getRequestTimeoutSeconds());
                    report.addCase("lifecycle.disconnect", E2EReport.PASSED, "channel disconnected and released local resources");
                } catch (Throwable error) {
                    report.addCase("lifecycle.disconnect", E2EReport.FAILED, report.safeError(error));
                }
            }
            report.write();
        }

        if (report.hasFailures()) {
            Assert.fail(report.failureSummary());
        }
    }

    private static LarkChannel createChannel(E2EConfig config, boolean enablePolicy) {
        LarkChannelOptions.Builder builder = LarkChannelOptions.newBuilder(config.getAppId(), config.getAppSecret())
                .transport("websocket");
        if (config.hasDomain()) {
            builder.domain(config.getDomain());
        }
        if (enablePolicy) {
            LarkChannelOptions.PolicyConfig policy = new LarkChannelOptions.PolicyConfig();
            policy.setGroupAllowlist(nonEmpty(config.getGroupChatId(), config.getAllowedGroupChatId()));
            policy.setDmMode("allowlist");
            policy.setDmAllowlist(nonEmpty(config.getReceiveOpenId(), config.getAllowedUserOpenId()));
            policy.setRequireMention(true);
            policy.setRespondToMentionAll(false);
            builder.policy(policy);
        }
        return LarkChannelFactory.createLarkChannel(builder.build());
    }

    private static void runAutomaticCases(final LarkChannel channel, final E2EConfig config,
                                          E2EPlan plan, final E2EReport report) {
        runCase(report, plan, "lifecycle.bot_identity", new CheckedAction() {
            @Override
            public void run() throws Exception {
                BotIdentity identity = channel.getBotIdentity();
                requireText(identity == null ? null : identity.getOpenId(), "bot identity is empty after connect");
            }
        }, config);

        runCase(report, plan, "chat.info", new CheckedAction() {
            @Override
            public void run() throws Exception {
                ChatInfo info = await(channel.getChatInfo(config.getGroupChatId()), config.getRequestTimeoutSeconds());
                requireText(info == null ? null : info.getChatId(), "chat info response has no chat id");
            }
        }, config);

        runCase(report, plan, "raw.message_list", new CheckedAction() {
            @Override
            public void run() throws Exception {
                long nowSeconds = System.currentTimeMillis() / 1000L;
                ListMessageResp response = channel.getRawClient().im().message().list(ListMessageReq.newBuilder()
                        .containerIdType("chat")
                        .containerId(config.getGroupChatId())
                        .startTime(String.valueOf(nowSeconds - 24L * 60L * 60L))
                        .endTime(String.valueOf(nowSeconds))
                        .sortType("ByCreateTimeDesc")
                        .pageSize(10)
                        .build());
                if (response == null) {
                    throw new IllegalStateException("message-list returned no response");
                }
                if (!response.success()) {
                    throw new IllegalStateException("message-list request failed with code " + response.getCode());
                }
            }
        }, config);

        runCase(report, plan, "send.text", new CheckedAction() {
            @Override
            public void run() throws Exception {
                send(channel, config, config.getReceiveOpenId(), SendInput.text("Channel SDK Java E2E text " + report.getTrace()), null);
            }
        }, config);

        runCase(report, plan, "send.markdown", new CheckedAction() {
            @Override
            public void run() throws Exception {
                send(channel, config, config.getReceiveOpenId(), SendInput.markdown("### Channel SDK Java E2E\n\n- case: send.markdown\n- trace: `" + report.getTrace() + "`"), null);
            }
        }, config);

        runCase(report, plan, "send.long_markdown", new CheckedAction() {
            @Override
            public void run() throws Exception {
                SendResult result = send(channel, config, config.getReceiveOpenId(), SendInput.markdown(longMarkdown(report.getTrace())), null);
                if (result.getChunkIds().size() < 2) {
                    throw new IllegalStateException("long markdown did not produce multiple chunks");
                }
            }
        }, config);

        runCase(report, plan, "send.post", new CheckedAction() {
            @Override
            public void run() throws Exception {
                send(channel, config, config.getReceiveOpenId(), SendInput.post(post(report.getTrace())), null);
            }
        }, config);

        runCase(report, plan, "send.group_mention", new CheckedAction() {
            @Override
            public void run() throws Exception {
                SendOptions options = SendOptions.newBuilder()
                        .mentions(Collections.singletonList(config.getMentionUserOpenId()))
                        .build();
                send(channel, config, config.getGroupChatId(), SendInput.text("Channel SDK Java E2E mention " + report.getTrace()), options);
            }
        }, config);

        runCase(report, plan, "send.group_text", new CheckedAction() {
            @Override
            public void run() throws Exception {
                send(channel, config, config.getGroupChatId(), SendInput.text("Channel SDK Java E2E group text " + report.getTrace()), null);
            }
        }, config);

        for (final String imageName : Arrays.asList("image.jpg", "image.png", "image.gif", "image.webp")) {
            runCase(report, plan, "send." + imageName, new CheckedAction() {
                @Override
                public void run() throws Exception {
                    send(channel, config, config.getReceiveOpenId(), SendInput.image(config.fixture(imageName).toString()), null);
                }
            }, config);
        }

        for (final String fileName : Arrays.asList("file.pdf", "file.docx", "file.xlsx", "file.pptx")) {
            runCase(report, plan, "send." + fileName, new CheckedAction() {
                @Override
                public void run() throws Exception {
                    send(channel, config, config.getReceiveOpenId(), SendInput.file(config.fixture(fileName).toString(), fileName), null);
                }
            }, config);
        }

        runCase(report, plan, "send.audio", new CheckedAction() {
            @Override
            public void run() throws Exception {
                send(channel, config, config.getReceiveOpenId(), SendInput.audio(config.fixture("audio.ogg").toString(), null), null);
            }
        }, config);

        runCase(report, plan, "send.video", new CheckedAction() {
            @Override
            public void run() throws Exception {
                send(channel, config, config.getReceiveOpenId(), SendInput.video(config.fixture("video.mp4").toString(), null, null), null);
            }
        }, config);

        runCase(report, plan, "send.share_chat", new CheckedAction() {
            @Override
            public void run() throws Exception {
                send(channel, config, config.getReceiveOpenId(), SendInput.shareChat(config.getShareChatId()), null);
            }
        }, config);

        runCase(report, plan, "send.share_user", new CheckedAction() {
            @Override
            public void run() throws Exception {
                send(channel, config, config.getReceiveOpenId(), SendInput.shareUser(config.getMentionUserOpenId()), null);
            }
        }, config);

        runCase(report, plan, "send.sticker", new CheckedAction() {
            @Override
            public void run() throws Exception {
                send(channel, config, config.getReceiveOpenId(), SendInput.sticker(config.getStickerFileKey()), null);
            }
        }, config);

        runCase(report, plan, "send.card", new CheckedAction() {
            @Override
            public void run() throws Exception {
                send(channel, config, config.getReceiveOpenId(), SendInput.card(card("send.card", report.getTrace())), null);
            }
        }, config);

        runCase(report, plan, "reply.text", new CheckedAction() {
            @Override
            public void run() throws Exception {
                SendResult base = send(channel, config, config.getReceiveOpenId(), SendInput.text("Channel SDK Java E2E reply base " + report.getTrace()), null);
                send(channel, config, config.getReceiveOpenId(), SendInput.text("Channel SDK Java E2E text reply " + report.getTrace()),
                        SendOptions.newBuilder().replyTo(base.getMessageId()).build());
            }
        }, config);

        runCase(report, plan, "reply.markdown", new CheckedAction() {
            @Override
            public void run() throws Exception {
                SendResult base = send(channel, config, config.getReceiveOpenId(), SendInput.text("Channel SDK Java E2E markdown reply base " + report.getTrace()), null);
                send(channel, config, config.getReceiveOpenId(), SendInput.markdown("reply markdown `" + report.getTrace() + "`"),
                        SendOptions.newBuilder().replyTo(base.getMessageId()).build());
            }
        }, config);

        runCase(report, plan, "reply.image", new CheckedAction() {
            @Override
            public void run() throws Exception {
                SendResult base = send(channel, config, config.getReceiveOpenId(), SendInput.text("Channel SDK Java E2E image reply base " + report.getTrace()), null);
                send(channel, config, config.getReceiveOpenId(), SendInput.image(config.fixture("image.jpg").toString()),
                        SendOptions.newBuilder().replyTo(base.getMessageId()).build());
            }
        }, config);

        runCase(report, plan, "message.edit", new CheckedAction() {
            @Override
            public void run() throws Exception {
                SendResult message = send(channel, config, config.getReceiveOpenId(), SendInput.text("Channel SDK Java E2E edit base " + report.getTrace()), null);
                await(channel.editMessage(message.getMessageId(), "Channel SDK Java E2E edit updated " + report.getTrace()), config.getRequestTimeoutSeconds());
            }
        }, config);

        runCase(report, plan, "card.update", new CheckedAction() {
            @Override
            public void run() throws Exception {
                SendResult message = send(channel, config, config.getReceiveOpenId(), SendInput.card(card("card.update.initial", report.getTrace())), null);
                await(channel.updateCard(message.getMessageId(), card("card.update.updated", report.getTrace())), config.getRequestTimeoutSeconds());
            }
        }, config);

        runCase(report, plan, "reaction.add_remove", new CheckedAction() {
            @Override
            public void run() throws Exception {
                SendResult message = send(channel, config, config.getReceiveOpenId(), SendInput.text("Channel SDK Java E2E reaction target " + report.getTrace()), null);
                String reactionId = await(channel.addReaction(message.getMessageId(), "THUMBSUP"), config.getRequestTimeoutSeconds());
                requireText(reactionId, "reaction id is empty");
                await(channel.removeReaction(message.getMessageId(), reactionId), config.getRequestTimeoutSeconds());
            }
        }, config);

        runCase(report, plan, "media.image_download", new CheckedAction() {
            @Override
            public void run() throws Exception {
                CreateImageResp response = channel.getRawClient().im().image().create(CreateImageReq.newBuilder()
                        .createImageReqBody(CreateImageReqBody.newBuilder()
                                .imageType("message")
                                .image(config.fixture("image.jpg").toFile())
                                .build())
                        .build());
                String imageKey = response == null || response.getData() == null ? null : response.getData().getImageKey();
                requireText(imageKey, "image upload returned an empty image key");
                requireBytes(await(channel.downloadResource(imageKey, "image"), config.getRequestTimeoutSeconds()), "image download returned no bytes");
            }
        }, config);

        runCase(report, plan, "media.file_download", new CheckedAction() {
            @Override
            public void run() throws Exception {
                CreateFileResp response = channel.getRawClient().im().file().create(CreateFileReq.newBuilder()
                        .createFileReqBody(CreateFileReqBody.newBuilder()
                                .fileType("pdf")
                                .fileName("channel-e2e-file.pdf")
                                .file(config.fixture("file.pdf").toFile())
                                .build())
                        .build());
                String fileKey = response == null || response.getData() == null ? null : response.getData().getFileKey();
                requireText(fileKey, "file upload returned an empty file key");
                requireBytes(await(channel.downloadResource(fileKey, "file"), config.getRequestTimeoutSeconds()), "file download returned no bytes");
            }
        }, config);

        runCase(report, plan, "stream.markdown", new CheckedAction() {
            @Override
            public void run() throws Exception {
                SendResult result = await(channel.stream(config.getReceiveOpenId(), StreamInput.markdown(new MarkdownStreamProducer() {
                    @Override
                    public void produce(MarkdownStreamController controller) {
                        controller.append("Channel SDK Java E2E stream start `" + report.getTrace() + "`");
                        controller.append("\n\nstream append `" + report.getTrace() + "`");
                    }
                })), config.getRequestTimeoutSeconds());
                requireText(result == null ? null : result.getMessageId(), "markdown stream returned an empty message id");
            }
        }, config);

        runCase(report, plan, "stream.card_update", new CheckedAction() {
            @Override
            public void run() throws Exception {
                final Map<String, Object> updated = card("stream.card_update.updated", report.getTrace());
                SendResult result = await(channel.stream(config.getReceiveOpenId(), StreamInput.card(card("stream.card_update.initial", report.getTrace()), new CardStreamProducer() {
                    @Override
                    public void produce(CardStreamController controller) {
                        controller.update(updated);
                    }
                })), config.getRequestTimeoutSeconds());
                requireText(result == null ? null : result.getMessageId(), "card stream returned an empty message id");
            }
        }, config);
    }

    private static void runManualCasesWhenEnabled(final LarkChannel channel, final E2EConfig config,
                                                   E2EPlan plan, final E2EReport report, EventTracker events) {
        if (!isTrue(System.getenv(MANUAL))) {
            report.markManualSkipped(plan, "set LARK_CHANNEL_E2E_MANUAL=true and trigger the documented user actions");
            return;
        }

        SendResult reactionTarget = null;
        try {
            reactionTarget = send(channel, config, config.getReceiveOpenId(), SendInput.text("Channel SDK Java E2E manual reaction target " + report.getTrace()), null);
            report.setManualInstruction("event.reaction_created", "add any reaction to the direct message beginning with ‘Channel SDK Java E2E manual reaction target’");
            report.setManualInstruction("event.reaction_deleted", "remove that same user-created reaction");
        } catch (Throwable error) {
            report.addCase("event.reaction_created", E2EReport.FAILED, report.safeError(error));
            report.addCase("event.reaction_deleted", E2EReport.FAILED, report.safeError(error));
        }

        try {
            send(channel, config, config.getReceiveOpenId(), SendInput.card(card("event.card_action", report.getTrace())), null);
            report.setManualInstruction("event.card_action", "click the E2E OK button on the direct card message");
        } catch (Throwable error) {
            report.addCase("event.card_action", E2EReport.FAILED, report.safeError(error));
        }

        report.setManualInstruction("event.message", "send the bot: Channel SDK Java E2E manual message " + report.getTrace());
        report.setManualInstruction("event.comment", "add one synthetic comment to the configured E2E document");
        if (isTrue(System.getenv(ENABLE_POLICY))) {
            report.setManualInstruction("policy.allowed_group", "send a bot-mention message in the allowed group");
            report.setManualInstruction("policy.blocked_group", "send a bot-mention message in the blocked group");
            report.setManualInstruction("policy.allowed_user", "send a direct message from the allowed user");
            report.setManualInstruction("policy.blocked_user", "send a direct message from the blocked user");
        } else {
            for (String caseId : Arrays.asList("policy.allowed_group", "policy.blocked_group", "policy.allowed_user", "policy.blocked_user")) {
                report.addCase(caseId, E2EReport.SKIPPED, "set LARK_CHANNEL_E2E_ENABLE_POLICY=true to run the policy event matrix");
            }
        }
        if (isTrue(System.getenv(ENABLE_BOT_ADDED))) {
            report.setManualInstruction("event.bot_added", "add the bot to the dedicated opt-in group");
        } else {
            report.addCase("event.bot_added", E2EReport.SKIPPED, "set LARK_CHANNEL_E2E_ENABLE_BOT_ADDED=true only for a dedicated group-membership test");
        }

        String reactionMessageId = reactionTarget == null ? null : reactionTarget.getMessageId();
        waitForManualCases(report, plan, events, reactionMessageId, config);
    }

    private static void waitForManualCases(E2EReport report, E2EPlan plan, EventTracker events,
                                           String reactionMessageId, E2EConfig config) {
        List<String> eventCaseIds = new ArrayList<String>(Arrays.asList("event.message", "event.card_action", "event.comment"));
        if (isTrue(System.getenv(ENABLE_POLICY))) {
            eventCaseIds.addAll(Arrays.asList("policy.allowed_group", "policy.blocked_group", "policy.allowed_user", "policy.blocked_user"));
        }
        if (isTrue(System.getenv(ENABLE_BOT_ADDED))) {
            eventCaseIds.add("event.bot_added");
        }
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(config.getManualWaitSeconds());
        try {
            events.awaitExpected(eventCaseIds, reactionMessageId, deadline);
        } catch (Throwable error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            report.addEnvironmentCheck("manual_event_wait", false, report.safeError(error));
        }
        for (String caseId : eventCaseIds) {
            if (!plan.isReady(caseId) || report.hasCase(caseId)) {
                continue;
            }
            String detail = events.observed(caseId);
            report.addCase(caseId, detail == null ? E2EReport.FAILED : E2EReport.PASSED,
                    detail == null ? "event was not observed within shared " + config.getManualWaitSeconds() + " second window" : detail);
        }
        for (String caseId : Arrays.asList("event.reaction_created", "event.reaction_deleted")) {
            if (!plan.isReady(caseId) || report.hasCase(caseId)) {
                continue;
            }
            String action = "event.reaction_created".equals(caseId) ? "added" : "removed";
            String detail = events.observedReaction(action, reactionMessageId);
            report.addCase(caseId, detail == null ? E2EReport.FAILED : E2EReport.PASSED,
                    detail == null ? "reaction was not observed within shared " + config.getManualWaitSeconds() + " second window" : detail);
        }
    }

    private static void runFocusedCommentCase(E2EConfig config, E2EPlan plan, E2EReport report, EventTracker events) {
        if (!plan.isReady("event.comment")) {
            report.addCase("event.comment", E2EReport.SKIPPED, plan.reason("event.comment"));
            return;
        }
        report.setManualInstruction("event.comment", "add one synthetic comment to the configured E2E document");
        report.addEnvironmentCheck("focused_comment_listener", true,
                "websocket connected; waiting for a comment event without running automatic cases");
        System.out.println("Channel E2E focused comment listener is ready");
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(config.getManualWaitSeconds());
        try {
            events.awaitAny(Arrays.asList("event.comment", "event.comment_unmatched"), deadline);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            report.addEnvironmentCheck("focused_comment_wait", false, report.safeError(error));
        }
        String matched = events.observed("event.comment");
        String unmatched = events.observed("event.comment_unmatched");
        if (matched != null) {
            report.addCase("event.comment", E2EReport.PASSED, matched);
        } else if (unmatched != null) {
            report.addCase("event.comment", E2EReport.FAILED,
                    "a comment event was received, but its file token did not match the configured E2E document");
        } else {
            report.addCase("event.comment", E2EReport.FAILED,
                    "this Java listener did not observe a comment event within " + config.getManualWaitSeconds() + " seconds");
        }
    }

    private static void registerEventHandlers(LarkChannel channel, final E2EConfig config, final String trace,
                                              final EventTracker events) {
        channel.on("message", new ChannelEventHandler<NormalizedMessage>() {
            @Override
            public void handle(NormalizedMessage event) {
                if (event != null && event.getContent() != null && event.getContent().contains(trace)) {
                    events.mark("event.message", "received trace-bearing inbound message");
                }
                if (event != null && config.getAllowedGroupChatId().equals(event.getChatId())) {
                    events.mark("policy.allowed_group", "received allowed group message");
                }
                if (event != null && config.getAllowedUserOpenId().equals(event.getSenderId())) {
                    events.mark("policy.allowed_user", "received allowed direct message");
                }
            }
        });
        channel.on("reaction", new ChannelEventHandler<ReactionEvent>() {
            @Override
            public void handle(ReactionEvent event) {
                if (event != null) {
                    events.markReaction(event.getAction(), event.getMessageId(), "received reaction " + event.getAction());
                }
            }
        });
        channel.on("cardAction", new ChannelEventHandler<CardActionEvent>() {
            @Override
            public void handle(CardActionEvent event) {
                if (event == null || event.getActionValue() == null) {
                    return;
                }
                Object caseValue = event.getActionValue().get("case");
                Object traceValue = event.getActionValue().get("trace");
                if ("event.card_action".equals(String.valueOf(caseValue)) && trace.equals(String.valueOf(traceValue))) {
                    events.mark("event.card_action", "received E2E card callback");
                }
            }
        });
        channel.on("comment", new ChannelEventHandler<CommentEvent>() {
            @Override
            public void handle(CommentEvent event) {
                if (event == null) {
                    return;
                }
                if (config.getDocToken().equals(event.getFileToken())) {
                    events.mark("event.comment", "received comment event for configured document");
                } else {
                    events.mark("event.comment_unmatched", "received comment event for a different document");
                }
            }
        });
        channel.on("botAdded", new ChannelEventHandler<com.lark.channel.model.BotAddedEvent>() {
            @Override
            public void handle(com.lark.channel.model.BotAddedEvent event) {
                if (event != null && config.getBotAddedChatId() != null && config.getBotAddedChatId().equals(event.getChatId())) {
                    events.mark("event.bot_added", "received bot-added event");
                }
            }
        });
        channel.on("reject", new ChannelEventHandler<RejectEvent>() {
            @Override
            public void handle(RejectEvent event) {
                if (event == null) {
                    return;
                }
                if (config.getBlockedGroupChatId().equals(event.getChatId())) {
                    events.mark("policy.blocked_group", "received group policy rejection");
                }
                if (config.getBlockedUserOpenId().equals(event.getSenderId())) {
                    events.mark("policy.blocked_user", "received direct-message policy rejection");
                }
            }
        });
    }

    private static void runCase(E2EReport report, E2EPlan plan, String caseId, CheckedAction action, E2EConfig config) {
        if (!plan.isReady(caseId)) {
            report.addCase(caseId, E2EReport.SKIPPED, plan.reason(caseId));
            return;
        }
        try {
            action.run();
            report.addCase(caseId, E2EReport.PASSED, "completed");
        } catch (Throwable error) {
            report.addCase(caseId, E2EReport.FAILED, report.safeError(error));
        }
    }

    private static SendResult send(LarkChannel channel, E2EConfig config, String target, SendInput input,
                                   SendOptions options) throws Exception {
        SendResult result = options == null
                ? await(channel.send(target, input), config.getRequestTimeoutSeconds())
                : await(channel.send(target, input, options), config.getRequestTimeoutSeconds());
        requireText(result == null ? null : result.getMessageId(), "send returned an empty message id");
        return result;
    }

    private static <T> T await(java.util.concurrent.CompletableFuture<T> future, int timeoutSeconds) throws Exception {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw error;
        } catch (TimeoutException error) {
            throw new IllegalStateException("operation timed out after " + timeoutSeconds + " seconds", error);
        }
    }

    private static Map<String, Object> post(String trace) {
        Map<String, Object> text = new LinkedHashMap<String, Object>();
        text.put("tag", "text");
        text.put("text", "Channel SDK Java E2E post " + trace);
        List<Map<String, Object>> row = new ArrayList<Map<String, Object>>();
        row.add(text);
        List<List<Map<String, Object>>> content = new ArrayList<List<Map<String, Object>>>();
        content.add(row);
        Map<String, Object> locale = new LinkedHashMap<String, Object>();
        locale.put("title", "Channel SDK Java E2E Post");
        locale.put("content", content);
        return Collections.<String, Object>singletonMap("zh_cn", locale);
    }

    private static Map<String, Object> card(String caseId, String trace) {
        Map<String, Object> title = new LinkedHashMap<String, Object>();
        title.put("tag", "plain_text");
        title.put("content", "Channel SDK Java E2E");
        Map<String, Object> header = new LinkedHashMap<String, Object>();
        header.put("title", title);
        Map<String, Object> text = new LinkedHashMap<String, Object>();
        text.put("tag", "lark_md");
        text.put("content", "**Channel SDK Java E2E**\ncase: `" + caseId + "`\ntrace: `" + trace + "`");
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("tag", "div");
        body.put("text", text);
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("case", caseId);
        value.put("trace", trace);
        Map<String, Object> buttonText = new LinkedHashMap<String, Object>();
        buttonText.put("tag", "plain_text");
        buttonText.put("content", "E2E OK");
        Map<String, Object> button = new LinkedHashMap<String, Object>();
        button.put("tag", "button");
        button.put("name", "channel_e2e_button");
        button.put("text", buttonText);
        button.put("type", "primary");
        button.put("value", value);
        Map<String, Object> action = new LinkedHashMap<String, Object>();
        action.put("tag", "action");
        action.put("actions", Collections.<Object>singletonList(button));
        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("config", Collections.<String, Object>singletonMap("wide_screen_mode", Boolean.TRUE));
        card.put("header", header);
        card.put("elements", Arrays.<Object>asList(body, action));
        return card;
    }

    private static String longMarkdown(String trace) {
        StringBuilder builder = new StringBuilder("# Channel SDK Java E2E Long Markdown\n\ntrace: `")
                .append(trace).append("`\n\n");
        for (int i = 0; i < 260; i++) {
            builder.append("long markdown paragraph ").append(trace).append('\n');
        }
        return builder.toString();
    }

    private static Path findRepositoryRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("pom.xml was not found from the current working directory");
    }

    private static String randomTrace() {
        byte[] random = new byte[6];
        new SecureRandom().nextBytes(random);
        StringBuilder suffix = new StringBuilder();
        for (byte value : random) {
            suffix.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return "channel-sdk-java-e2e-" + System.currentTimeMillis() + "-" + suffix;
    }

    private static List<String> nonEmpty(String... values) {
        List<String> out = new ArrayList<String>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !out.contains(value)) {
                out.add(value);
            }
        }
        return out;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireBytes(byte[] value, String message) {
        if (value == null || value.length == 0) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean isTrue(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private static String join(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private interface CheckedAction {
        void run() throws Exception;
    }

    private static final class E2EConfig {
        private final Path root;
        private final Map<String, String> values;

        private E2EConfig(Path root, Map<String, String> values) {
            this.root = root;
            this.values = values;
        }

        static E2EConfig load(Path root, Map<String, String> environment) throws IOException {
            Map<String, String> values = new LinkedHashMap<String, String>();
            Path dotenv = root.resolve(".env");
            if (Files.isRegularFile(dotenv, LinkOption.NOFOLLOW_LINKS)) {
                parseDotenv(Files.newBufferedReader(dotenv, StandardCharsets.UTF_8), values);
            }
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                values.put(entry.getKey(), entry.getValue());
            }
            return new E2EConfig(root, values);
        }

        private static void parseDotenv(Reader reader, Map<String, String> values) throws IOException {
            BufferedReader buffered = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
            try {
                String line;
                int lineNumber = 0;
                while ((line = buffered.readLine()) != null) {
                    lineNumber++;
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    if (trimmed.startsWith("export ")) {
                        trimmed = trimmed.substring("export ".length()).trim();
                    }
                    int separator = trimmed.indexOf('=');
                    if (separator <= 0) {
                        throw new IOException(".env line " + lineNumber + " must use KEY=VALUE syntax");
                    }
                    String key = trimmed.substring(0, separator).trim();
                    String value = trimmed.substring(separator + 1).trim();
                    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    values.put(key, value);
                }
            } finally {
                buffered.close();
            }
        }

        List<String> validate() {
            List<String> errors = new ArrayList<String>();
            for (String required : Arrays.asList("APP_ID", "APP_SECRET", "CHANNEL_E2E_RECEIVE_OPEN_ID", "CHANNEL_E2E_GROUP_CHAT_ID", "CHANNEL_E2E_MENTION_USER_OPEN_ID")) {
                if (!has(required)) {
                    errors.add(required + " is required");
                }
            }
            for (String file : Arrays.asList("image.jpg", "image.png", "image.gif", "image.webp", "file.pdf", "file.docx", "file.xlsx", "file.pptx", "audio.ogg", "video.mp4")) {
                try {
                    fixture(file);
                } catch (IOException error) {
                    errors.add("fixture " + file + ": " + error.getMessage());
                }
            }
            return errors;
        }

        Path fixture(String name) throws IOException {
            String configured = value(fixtureVariable(name));
            if (configured == null || configured.trim().isEmpty()) {
                configured = "./testdata/e2e/" + name;
            }
            Path candidate = root.resolve(configured).normalize();
            if (!candidate.startsWith(root)) {
                throw new IOException("path traversal is not allowed");
            }
            if (Files.isSymbolicLink(candidate)) {
                throw new IOException("symbolic-link fixtures are not allowed");
            }
            if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) || Files.size(candidate) == 0L) {
                throw new IOException("fixture is missing or empty");
            }
            return candidate;
        }

        private String fixtureVariable(String name) {
            if ("image.jpg".equals(name)) return "CHANNEL_E2E_IMAGE_JPG";
            if ("image.png".equals(name)) return "CHANNEL_E2E_IMAGE_PNG";
            if ("image.gif".equals(name)) return "CHANNEL_E2E_IMAGE_GIF";
            if ("image.webp".equals(name)) return "CHANNEL_E2E_IMAGE_WEBP";
            if ("file.pdf".equals(name)) return "CHANNEL_E2E_FILE_PDF";
            if ("file.docx".equals(name)) return "CHANNEL_E2E_FILE_DOCX";
            if ("file.xlsx".equals(name)) return "CHANNEL_E2E_FILE_XLSX";
            if ("file.pptx".equals(name)) return "CHANNEL_E2E_FILE_PPTX";
            if ("audio.ogg".equals(name)) return "CHANNEL_E2E_AUDIO_OGG";
            if ("video.mp4".equals(name)) return "CHANNEL_E2E_VIDEO_MP4";
            throw new IllegalArgumentException("unknown fixture: " + name);
        }

        String getAppId() { return value("APP_ID"); }
        String getAppSecret() { return value("APP_SECRET"); }
        String getReceiveOpenId() { return value("CHANNEL_E2E_RECEIVE_OPEN_ID"); }
        String getGroupChatId() { return value("CHANNEL_E2E_GROUP_CHAT_ID"); }
        String getMentionUserOpenId() { return value("CHANNEL_E2E_MENTION_USER_OPEN_ID"); }
        String getDocToken() { return value("CHANNEL_E2E_DOC_TOKEN"); }
        String getAllowedGroupChatId() { return value("CHANNEL_E2E_ALLOWED_GROUP_CHAT_ID"); }
        String getBlockedGroupChatId() { return value("CHANNEL_E2E_BLOCKED_GROUP_CHAT_ID"); }
        String getAllowedUserOpenId() { return value("CHANNEL_E2E_ALLOWED_USER_OPEN_ID"); }
        String getBlockedUserOpenId() { return value("CHANNEL_E2E_BLOCKED_USER_OPEN_ID"); }
        String getShareChatId() { return value("CHANNEL_E2E_SHARE_CHAT_ID"); }
        String getBotAddedChatId() { return value("CHANNEL_E2E_BOT_ADDED_CHAT_ID"); }
        String getStickerFileKey() { return value("CHANNEL_E2E_STICKER_FILE_KEY"); }
        String getDomain() { return value("CHANNEL_E2E_DOMAIN"); }
        boolean hasDomain() { return has("CHANNEL_E2E_DOMAIN"); }
        int getConnectTimeoutSeconds() { return positiveInt("CHANNEL_E2E_CONNECT_TIMEOUT_SECONDS", 45); }
        int getRequestTimeoutSeconds() { return positiveInt("CHANNEL_E2E_REQUEST_TIMEOUT_SECONDS", 30); }
        int getManualWaitSeconds() { return positiveInt("CHANNEL_E2E_WAIT_SECONDS", 180); }

        private String value(String key) { return values.get(key); }
        private boolean has(String key) { return value(key) != null && !value(key).trim().isEmpty(); }
        private int positiveInt(String key, int fallback) {
            try {
                int value = Integer.parseInt(value(key));
                return value > 0 ? value : fallback;
            } catch (Exception ignored) {
                return fallback;
            }
        }
    }

    private static final class E2EPlan {
        private final Map<String, String> unavailable = new LinkedHashMap<String, String>();

        static E2EPlan create(E2EConfig config) {
            E2EPlan plan = new E2EPlan();
            if (config.getStickerFileKey() == null || config.getStickerFileKey().trim().isEmpty()) {
                plan.unavailable.put("send.sticker", "CHANNEL_E2E_STICKER_FILE_KEY is not configured");
            }
            return plan;
        }

        boolean isReady(String caseId) { return !unavailable.containsKey(caseId); }
        String reason(String caseId) { return unavailable.get(caseId); }
    }

    private static final class EventTracker {
        private final Map<String, String> seen = new LinkedHashMap<String, String>();
        private final List<ReactionObservation> reactions = new ArrayList<ReactionObservation>();

        synchronized void mark(String caseId, String detail) {
            if (!seen.containsKey(caseId)) {
                seen.put(caseId, detail);
                notifyAll();
            }
        }

        synchronized void markReaction(String action, String messageId, String detail) {
            reactions.add(new ReactionObservation(action, messageId, detail));
            notifyAll();
        }

        synchronized void awaitExpected(List<String> eventCaseIds, String reactionMessageId, long deadline) throws InterruptedException {
            while (!allObserved(eventCaseIds, reactionMessageId)) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    return;
                }
                wait(remaining);
            }
        }

        synchronized String observed(String caseId) {
            return seen.get(caseId);
        }

        synchronized void awaitAny(List<String> caseIds, long deadline) throws InterruptedException {
            while (!anyObserved(caseIds)) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    return;
                }
                wait(remaining);
            }
        }

        synchronized String observedReaction(String action, String messageId) {
            for (ReactionObservation observation : reactions) {
                if (action.equals(observation.action) && messageId != null && messageId.equals(observation.messageId)) {
                    return observation.detail;
                }
            }
            return null;
        }

        private boolean allObserved(List<String> eventCaseIds, String reactionMessageId) {
            for (String caseId : eventCaseIds) {
                if (!seen.containsKey(caseId)) {
                    return false;
                }
            }
            return reactionMessageId == null || (observedReaction("added", reactionMessageId) != null
                    && observedReaction("removed", reactionMessageId) != null);
        }

        private boolean anyObserved(List<String> caseIds) {
            for (String caseId : caseIds) {
                if (seen.containsKey(caseId)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class ReactionObservation {
        private final String action;
        private final String messageId;
        private final String detail;

        private ReactionObservation(String action, String messageId, String detail) {
            this.action = action;
            this.messageId = messageId;
            this.detail = detail;
        }
    }

    private static final class E2EReport {
        static final String PASSED = "passed";
        static final String FAILED = "failed";
        static final String SKIPPED = "skipped";

        private final Path root;
        private final E2EConfig config;
        private final String trace;
        private final String startedAt;
        private final Map<String, CaseResult> cases = new LinkedHashMap<String, CaseResult>();
        private final List<String> environmentChecks = new ArrayList<String>();
        private final Map<String, String> manualInstructions = new LinkedHashMap<String, String>();

        E2EReport(Path root, E2EConfig config, String trace) {
            this.root = root;
            this.config = config;
            this.trace = trace;
            this.startedAt = now();
        }

        String getTrace() { return trace; }
        boolean hasCase(String caseId) { return cases.containsKey(caseId); }
        boolean isPassed(String caseId) { return cases.containsKey(caseId) && PASSED.equals(cases.get(caseId).status); }
        boolean hasFailures() {
            for (CaseResult result : cases.values()) {
                if (FAILED.equals(result.status)) return true;
            }
            return false;
        }

        void addEnvironmentCheck(String name, boolean passed, String detail) {
            environmentChecks.add(name + ": " + (passed ? PASSED : FAILED) + " — " + redact(detail));
        }

        void recordPlan(E2EPlan plan) {
            if (!plan.isReady("send.sticker")) {
                addCase("send.sticker", SKIPPED, plan.reason("send.sticker"));
            }
        }

        void addCase(String caseId, String status, String detail) {
            cases.put(caseId, new CaseResult(status, redact(detail)));
        }

        void setManualInstruction(String caseId, String instruction) {
            manualInstructions.put(caseId, instruction);
        }

        void markManualSkipped(E2EPlan plan, String reason) {
            for (String caseId : Arrays.asList("event.message", "event.reaction_created", "event.reaction_deleted", "event.card_action", "event.comment",
                    "policy.allowed_group", "policy.blocked_group", "policy.allowed_user", "policy.blocked_user")) {
                if (plan.isReady(caseId) && !cases.containsKey(caseId)) {
                    addCase(caseId, SKIPPED, reason);
                }
            }
            if (!cases.containsKey("event.bot_added")) {
                addCase("event.bot_added", SKIPPED, "requires LARK_CHANNEL_E2E_MANUAL=true and LARK_CHANNEL_E2E_ENABLE_BOT_ADDED=true");
            }
        }

        void markBlockedRemainingAutomaticCases(String reason) {
            addEnvironmentCheck("automatic_cases", false, reason);
        }

        String safeError(Throwable error) {
            Throwable current = error;
            while (current instanceof ExecutionException && current.getCause() != null) {
                current = current.getCause();
            }
            String message = current.getMessage();
            return current.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        }

        String failureSummary() {
            List<String> failed = new ArrayList<String>();
            for (Map.Entry<String, CaseResult> entry : cases.entrySet()) {
                if (FAILED.equals(entry.getValue().status)) {
                    failed.add(entry.getKey());
                }
            }
            return "Channel E2E failures: " + join(failed, ", ") + "; report=" + reportPath();
        }

        void write() {
            try {
                Path directory = root.resolve("target").resolve("e2e");
                Files.createDirectories(directory);
                Files.write(reportPath(), render().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException error) {
                throw new IllegalStateException("write E2E report", error);
            }
        }

        private Path reportPath() {
            return root.resolve("target").resolve("e2e").resolve(trace + ".md");
        }

        private String render() {
            StringBuilder out = new StringBuilder();
            out.append("# Channel SDK Java E2E Report\n\n");
            out.append("- trace: `").append(trace).append("`\n");
            out.append("- started_at: ").append(startedAt).append("\n");
            out.append("- finished_at: ").append(now()).append("\n");
            out.append("- app_id: ").append(mask(config.getAppId())).append("\n");
            out.append("- receive_open_id: ").append(mask(config.getReceiveOpenId())).append("\n\n");
            out.append("## Environment checks\n\n");
            for (String check : environmentChecks) out.append("- ").append(check).append("\n");
            out.append("\n## Cases\n\n| Case | Status | Detail |\n| --- | --- | --- |\n");
            for (Map.Entry<String, CaseResult> entry : cases.entrySet()) {
                out.append("| `").append(entry.getKey()).append("` | ").append(entry.getValue().status).append(" | ")
                        .append(entry.getValue().detail.replace("|", "\\|")).append(" |\n");
            }
            if (!manualInstructions.isEmpty()) {
                out.append("\n## Manual instructions\n\n");
                for (Map.Entry<String, String> entry : manualInstructions.entrySet()) {
                    out.append("- `").append(entry.getKey()).append("`: ").append(entry.getValue()).append("\n");
                }
            }
            return out.toString();
        }

        private String redact(String value) {
            if (value == null) return "";
            String out = value;
            for (String secret : Arrays.asList(config.getAppSecret(), config.getDocToken(), config.getReceiveOpenId(), config.getGroupChatId(),
                    config.getMentionUserOpenId(), config.getAllowedGroupChatId(), config.getBlockedGroupChatId(), config.getAllowedUserOpenId(),
                    config.getBlockedUserOpenId(), config.getShareChatId(), config.getBotAddedChatId(), config.getStickerFileKey())) {
                if (secret != null && !secret.isEmpty()) out = out.replace(secret, mask(secret));
            }
            return out.length() > 480 ? out.substring(0, 480) + "…" : out;
        }

        private static String now() {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT);
            format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            return format.format(new Date());
        }

        private static String mask(String value) {
            if (value == null || value.isEmpty()) return "<empty>";
            if (value.length() <= 8) return "********";
            return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
        }
    }

    private static final class CaseResult {
        private final String status;
        private final String detail;

        private CaseResult(String status, String detail) {
            this.status = status;
            this.detail = detail;
        }
    }
}
