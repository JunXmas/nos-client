package dev.jun.nosclient.lyricsflow.media;

import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import dev.jun.nosclient.lyricsflow.LyricsFlow;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AppVolume {
   private static final Memory CLSID_ENUMERATOR = Com.guid("bcde0395-e52f-467c-8e3d-c4579291692e");
   private static final Memory IID_ENUMERATOR = Com.guid("a95664d2-9614-4f35-a746-de8db63617e6");
   private static final Memory IID_SESSION_MANAGER2 = Com.guid("77aa99a0-1bd6-484f-8bc7-2c654c9a9b6f");
   private static final Memory IID_SESSION_CONTROL2 = Com.guid("bfb7ff88-7239-4fc9-8fa2-07c950be9c6d");
   private static final Memory IID_SIMPLE_VOLUME = Com.guid("87ce5498-68d6-44e5-9215-6da47ef883d8");
   private static final Set<String> BROWSERS = Set.of(
      "chrome.exe",
      "msedge.exe",
      "firefox.exe",
      "opera.exe",
      "opera_gx.exe",
      "brave.exe",
      "vivaldi.exe",
      "browser.exe",
      "arc.exe",
      "zen.exe",
      "librewolf.exe",
      "waterfox.exe"
   );
   private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "LyricsFlow volume");
      t.setDaemon(true);
      return t;
   });
   private static final AtomicBoolean busy = new AtomicBoolean();
   private static volatile float volume = -1.0F;
   private static volatile boolean muted;
   private static volatile long lastRead;
   private static volatile float pendingSet = -1.0F;
   private static boolean inited;
   private static boolean failed;
   private static NativeLibrary ole32;
   private static NativeLibrary kernel32;

   private AppVolume() {
   }

   public static boolean supported() {
      return Platform.isWindows() && !failed;
   }

   public static float get() {
      if (!supported()) {
         return -1.0F;
      }

      if (System.currentTimeMillis() - lastRead > 1000L && busy.compareAndSet(false, true)) {
         EXEC.execute(() -> {
            try {
               apply(-1.0F, null);
            } finally {
               busy.set(false);
            }
         });
      }

      return volume;
   }

   public static boolean muted() {
      return muted;
   }

   public static void set(float v) {
      if (supported()) {
         v = Math.max(0.0F, Math.min(1.0F, v));
         volume = v;
         pendingSet = v;
         EXEC.execute(() -> {
            float p = pendingSet;
            if (!(p < 0.0F)) {
               pendingSet = -1.0F;
               apply(p, null);
            }
         });
      }
   }

   public static void toggleMute() {
      if (supported()) {
         boolean m = !muted;
         muted = m;
         EXEC.execute(() -> apply(-1.0F, m));
      }
   }

   private static boolean init() {
      if (inited) {
         return !failed;
      }

      inited = true;

      try {
         ole32 = NativeLibrary.getInstance("ole32");
         kernel32 = NativeLibrary.getInstance("kernel32");
         ole32.getFunction("CoInitializeEx").invokeInt(new Object[]{null, 0});
         return true;
      } catch (Throwable t) {
         LyricsFlow.LOG.warn("App volume unavailable", t);
         failed = true;
         return false;
      }
   }

   private static boolean wanted(String exe) {
      LFConfig.Source s = ConfigManager.get().source;
      return s == LFConfig.Source.YOUTUBE ? BROWSERS.contains(exe) : exe.equals("spotify.exe");
   }

   private static String exeName(int pid) {
      Pointer h = kernel32.getFunction("OpenProcess").invokePointer(new Object[]{4096, 0, pid});
      if (h == null) {
         return "";
      }

      try {
         Memory buf = new Memory(2048L);
         IntByReference size = new IntByReference(1024);
         int ok = kernel32.getFunction("QueryFullProcessImageNameW").invokeInt(new Object[]{h, 0, buf, size});
         if (ok == 0) {
            return "";
         }

         String path = buf.getWideString(0L);
         int i = Math.max(path.lastIndexOf(92), path.lastIndexOf(47));
         return path.substring(i + 1).toLowerCase(Locale.ROOT);
      } finally {
         kernel32.getFunction("CloseHandle").invokeInt(new Object[]{h});
      }
   }

   private static void apply(float setVolume, Boolean setMute) {
      if (init()) {
         Pointer enumerator = null;
         Pointer device = null;
         Pointer manager = null;
         Pointer sessions = null;
         List<Pointer> held = new ArrayList<>();

         try {
            PointerByReference r = new PointerByReference();
            int hr = ole32.getFunction("CoCreateInstance").invokeInt(new Object[]{CLSID_ENUMERATOR, null, 23, IID_ENUMERATOR, r});
            if (hr < 0) {
               return;
            }

            enumerator = r.getValue();
            device = Com.outPtr(enumerator, 4, 0, 1);
            if (device == null) {
               return;
            }

            manager = Com.outPtr(device, 3, IID_SESSION_MANAGER2, 23, null);
            if (manager == null) {
               return;
            }

            sessions = Com.outPtr(manager, 5);
            if (sessions != null) {
               int count = Com.outInt(sessions, 3);
               float found = -1.0F;
               boolean anyMuted = false;

               for (int i = 0; i < count; i++) {
                  Pointer ctl = Com.outPtr(sessions, 4, i);
                  if (ctl != null) {
                     held.add(ctl);
                     Pointer ctl2 = Com.queryInterface(ctl, IID_SESSION_CONTROL2);
                     if (ctl2 != null) {
                        held.add(ctl2);
                        int pid = Com.outInt(ctl2, 14);
                        if (pid > 0 && wanted(exeName(pid))) {
                           Pointer vol = Com.queryInterface(ctl, IID_SIMPLE_VOLUME);
                           if (vol != null) {
                              held.add(vol);
                              if (setVolume >= 0.0F) {
                                 Com.call(vol, 3, setVolume, null);
                              }

                              if (setMute != null) {
                                 Com.call(vol, 5, setMute ? 1 : 0, null);
                              }

                              FloatByReference f = new FloatByReference();
                              if (Com.call(vol, 4, f) >= 0) {
                                 found = Math.max(found, f.getValue());
                              }

                              IntByReference m = new IntByReference();
                              if (Com.call(vol, 6, m) >= 0 && m.getValue() != 0) {
                                 anyMuted = true;
                              }
                           }
                        }
                     }
                  }
               }

               if (pendingSet < 0.0F) {
                  volume = found;
               }

               muted = anyMuted;
               lastRead = System.currentTimeMillis();
               return;
            }
         } catch (Throwable t) {
            LyricsFlow.LOG.debug("App volume failed", t);
            lastRead = System.currentTimeMillis();
            return;
         } finally {
            for (Pointer p : held) {
               Com.release(p);
            }

            Com.release(sessions);
            Com.release(manager);
            Com.release(device);
            Com.release(enumerator);
         }
      }
   }
}
