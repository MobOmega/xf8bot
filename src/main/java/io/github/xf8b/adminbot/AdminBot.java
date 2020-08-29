/*
 * Copyright (c) 2020 xf8b.
 *
 * This file is part of AdminBot.
 *
 * AdminBot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdminBot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdminBot.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.xf8b.adminbot;

import com.beust.jcommander.JCommander;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import discord4j.core.shard.ShardingStrategy;
import discord4j.rest.util.Color;
import io.github.xf8b.adminbot.api.commands.AbstractCommandHandler;
import io.github.xf8b.adminbot.api.commands.CommandFiredEvent;
import io.github.xf8b.adminbot.handlers.SlapBrigadierCommand;
import io.github.xf8b.adminbot.listeners.MessageListener;
import io.github.xf8b.adminbot.listeners.ReadyListener;
import io.github.xf8b.adminbot.settings.BotConfiguration;
import io.github.xf8b.adminbot.util.CommandRegistry;
import io.github.xf8b.adminbot.util.FileUtil;
import io.github.xf8b.adminbot.util.LogbackUtil;
import io.github.xf8b.adminbot.util.ShutdownHandler;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Slf4j
public class AdminBot {
    private final String version;
    private final CommandRegistry commandRegistry = new CommandRegistry();
    private final GatewayDiscordClient client;
    @Getter(AccessLevel.NONE)
    private final BotConfiguration botConfiguration;

    private AdminBot(BotConfiguration botConfiguration) throws IOException, URISyntaxException {
        //TODO: member verifying system
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        URL url = classloader.getResource("version.txt");
        if (url == null) throw new NullPointerException("The version file does not exist!");
        version = Files.readAllLines(Paths.get(url.toURI())).get(0);
        client = DiscordClient.create(botConfiguration.getToken())
                .gateway()
                .setSharding(ShardingStrategy.recommended())
                .setInitialStatus(shardInfo -> Presence.online(Activity.playing(String.format(
                        "%s | Shard ID: %d",
                        botConfiguration.getActivity(), shardInfo.getIndex()
                ))))
                .login()
                .doOnError(throwable -> {
                    LOGGER.error("Could not login!", throwable);
                    ShutdownHandler.shutdownWithError(throwable);
                })
                .block();
        this.botConfiguration = botConfiguration;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ShutdownHandler.shutdown();
            client.logout().block();
        }));
        FileUtil.createFolders();
        FileUtil.createFiles();
        //here just because i want it to be in help command
        commandRegistry.registerCommandHandlers(new AbstractCommandHandler(AbstractCommandHandler.builder()
                .setName("${prefix}slap")
                .setDescription("Slaps the person.")
                .setCommandType(AbstractCommandHandler.CommandType.OTHER)
                .setMinimumAmountOfArgs(1)) {
            @Override
            public void onCommandFired(CommandFiredEvent event) {

            }
        });
        commandRegistry.slurpCommandHandlers("io.github.xf8b.adminbot.handlers");
        MessageListener messageListener = new MessageListener(this, commandRegistry);
        ReadyListener readyListener = new ReadyListener(
                botConfiguration.getActivity(),
                botConfiguration.getBotAdministrators(),
                version
        );
        //TODO: figure out why readyevent isnt being fired
        client.on(ReadyEvent.class).subscribe(readyListener::onReadyEvent);
        client.getEventDispatcher().on(ReadyEvent.class).subscribe(readyEvent -> System.out.println("ReadyEvent fired!"));
        client.on(MessageCreateEvent.class)
                .filter(event -> !event.getMessage().getContent().isEmpty())
                .filter(event -> event.getMember().isPresent())
                .filter(event -> event.getMessage().getAuthor().isPresent())
                .filter(event -> !event.getMessage().getAuthor().get().isBot())
                .subscribe(messageListener::onMessageCreateEvent);
        CommandDispatcher<MessageCreateEvent> commandDispatcher = new CommandDispatcher<>();
        SlapBrigadierCommand.register(commandDispatcher);
        client.on(MessageCreateEvent.class).subscribe(messageCreateEvent -> {
            if (messageCreateEvent.getMember().isEmpty()) return;
            if (messageCreateEvent.getMember().get().isBot()) return;
            if (!messageCreateEvent.getMessage().getContent().startsWith(">slap")) return;
            try {
                commandDispatcher.execute(messageCreateEvent.getMessage().getContent(), messageCreateEvent);
            } catch (CommandSyntaxException exception) {
                messageCreateEvent.getMessage()
                        .getChannel()
                        .flatMap(messageChannel -> messageChannel.createMessage("CommandSyntaxException: " + exception))
                        .subscribe();
            }
        });
        User self = client.getSelf().block();
        if (!botConfiguration.getLogDumpWebhook().isBlank()) {
            Pair<Snowflake, String> webhookIdAndToken = parseWebhookUrl(botConfiguration.getLogDumpWebhook());
            //TODO: move logging to webhooks
            client.getWebhookByIdWithToken(webhookIdAndToken.getLeft(), webhookIdAndToken.getRight())
                    .flatMap(webhook -> webhook.execute(webhookExecuteSpec -> webhookExecuteSpec.setAvatarUrl(self.getAvatarUrl())
                            .setUsername(self.getUsername())
                            .addEmbed(embedCreateSpec -> embedCreateSpec.setTitle(":warning: Bot was restarted! :warning:")
                                    .setDescription("This is a new run!")
                                    .setColor(Color.YELLOW)
                                    .setTimestamp(Instant.now()))))
                    .subscribe();
        }
        LogbackUtil.setupDiscordAppender(botConfiguration.getLogDumpWebhook(), self.getUsername(), self.getAvatarUrl());
        client.onDisconnect().block();
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        BotConfiguration botConfiguration = new BotConfiguration(
                "baseConfig.toml",
                "secrets/config.toml"
        );
        JCommander.newBuilder()
                .addObject(botConfiguration)
                .build()
                .parse(args);
        new AdminBot(botConfiguration);
    }

    private Pair<Snowflake, String> parseWebhookUrl(String webhookUrl) {
        Pattern pattern = Pattern.compile("https://discordapp\\.com/api/webhooks/(\\d+)/(.+)");
        Matcher matcher = pattern.matcher(webhookUrl);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid webhook URL!");
        } else {
            String id = matcher.group(1);
            String token = matcher.group(2);
            return Pair.of(Snowflake.of(id), token);
        }
    }

    public boolean isBotAdministrator(Snowflake snowflake) {
        return botConfiguration.getBotAdministrators().contains(snowflake);
    }

    public <T> void setBotSetting(String name, T newValue) {
        botConfiguration.set(name, newValue);
    }
}
