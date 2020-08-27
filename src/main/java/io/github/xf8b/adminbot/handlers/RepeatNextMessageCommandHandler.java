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

package io.github.xf8b.adminbot.handlers;

import io.github.xf8b.adminbot.api.commands.AbstractCommandHandler;
import io.github.xf8b.adminbot.api.commands.CommandFiredEvent;

public class RepeatNextMessageCommandHandler extends AbstractCommandHandler {
    public RepeatNextMessageCommandHandler() {
        super(AbstractCommandHandler.builder()
                .setName("${prefix}repeat")
                .setDescription("Repeats the next message. TODO: delete")
                .setCommandType(CommandType.OTHER));
    }

    @Override
    public void onCommandFired(CommandFiredEvent event) {
        //TODO: fix
        /*
        event.getClient().on(MessageCreateEvent.class)
                .timeout(Duration.ofSeconds(2))
                .filterWhen(messageCreateEvent -> messageCreateEvent
                        .getMessage()
                        .getChannel()
                        .flux()
                        .any(messageChannel -> messageChannel == event.getChannel().block()))
                .flatMap(messageCreateEvent -> messageCreateEvent.getMessage().getChannel()
                        .flatMap(messageChannel -> messageChannel.createMessage(messageCreateEvent.getMessage()
                                .getContent())))
                .subscribe();
                */
        event.getChannel()
                .flatMap(messageChannel -> messageChannel.createMessage("Do not use this command!"))
                .subscribe();
    }
}
