package com.andresblue.threatdirector.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ThreatDirectorCommandsTest {
    @Test
    void registersThreatOverridesAndSceneControls() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        ThreatDirectorCommands.register(dispatcher, Optional::empty);

        CommandNode<ServerCommandSource> root = dispatcher.getRoot().getChild("threatdirector");
        assertNotNull(root);
        CommandNode<ServerCommandSource> threat = root.getChild("threat");
        assertNotNull(threat);
        assertNotNull(threat.getChild("set"));
        assertNotNull(threat.getChild("clear"));
        assertNotNull(root.getChild("scene"));
        CommandNode<ServerCommandSource> hud = root.getChild("hud");
        assertNotNull(hud);
        assertNotNull(hud.getChild("on"));
        assertNotNull(hud.getChild("off"));
    }
}
