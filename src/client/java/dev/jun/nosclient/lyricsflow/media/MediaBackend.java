package dev.jun.nosclient.lyricsflow.media;

import dev.jun.nosclient.lyricsflow.config.LFConfig;

public interface MediaBackend {
   Track poll(LFConfig.Source var1) throws Exception;

   byte[] cover();

   void playPause();

   void next();

   void previous();

   default int repeatMode() {
      return -1;
   }

   default void setRepeat(int mode) {
   }

   default void seek(long ms) {
   }

   String statusKey();

   boolean available();

   void close();
}
