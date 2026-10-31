package dev.jun.nosclient.lyricsflow;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LyricsFlow {
   public static final String MOD_ID = "lyricsflow";
   public static final String NAME = "LyricsFlow";
   public static final String VERSION_LABEL = "v2.1-beta";
   public static final String CREDIT_DC = "dc: cmonmanx";
   public static final String SERVER_URL = "https://discord.gg/LyricsFlow";
   public static final String CREDIT_SERVER = "server: discord.gg/LyricsFlow";
   public static final Logger LOG = LoggerFactory.getLogger("LyricsFlow");

   private LyricsFlow() {
   }

   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("lyricsflow", path);
   }
}
