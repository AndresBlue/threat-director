package com.andresblue.threatdirector.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ThreatDirectorCommandsTest {
    @Test
    void registersCompactThreatHudAndDebugControls() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        ThreatDirectorCommands.register(dispatcher, Optional::empty);

        CommandNode<ServerCommandSource> root = dispatcher.getRoot().getChild("threatdirector");
        assertNotNull(root);
        CommandNode<ServerCommandSource> threat = root.getChild("threat");
        assertNotNull(threat);
        assertNotNull(threat.getChild("set"));
        assertNotNull(threat.getChild("clear"));
        CommandNode<ServerCommandSource> hud = root.getChild("hud");
        assertNotNull(hud);
        assertNotNull(hud.getChild("on"));
        assertNotNull(hud.getChild("off"));
        CommandNode<ServerCommandSource> performance = root.getChild("debug").getChild("performance");
        assertNotNull(performance);
        assertNotNull(performance.getChild("reset"));
    }

    @Test
    void registersCompactOperatorLoggingAndDebugSurface() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        ThreatDirectorCommands.register(dispatcher, Optional::empty);

        CommandNode<ServerCommandSource> root = dispatcher.getRoot().getChild("threatdirector");
        CommandNode<ServerCommandSource> logging = root.getChild("logging");
        assertNotNull(logging);
        assertNotNull(logging.getChild("on"));
        assertNotNull(logging.getChild("off"));
        assertNotNull(logging.getChild("status"));
        CommandNode<ServerCommandSource> debug = root.getChild("debug");
        assertNotNull(debug);
        assertNotNull(debug.getChild("scene"));
        assertNotNull(debug.getChild("performance"));
        assertNotNull(debug.getChild("telemetry"));
    }
}
