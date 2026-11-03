package dev.jun.nosclient;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientResources {
    public static final String MOD_ID = "nosclient";
    public static final Logger LOG = LoggerFactory.getLogger("nosclient");

    private ClientResources() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("nosclient", path);
    }
}
