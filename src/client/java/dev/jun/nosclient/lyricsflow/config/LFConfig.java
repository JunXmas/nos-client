package dev.jun.nosclient.lyricsflow.config;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class LFConfig {
   public int configVersion = 18;
   public boolean enabled = true;
   public boolean uiSounds = true;
   public float uiVolume = 0.6F;
   public int soundStyle = 0;
   public boolean songChime = false;
   public LFConfig.Source source = LFConfig.Source.SPOTIFY;
   public int syncOffsetMs = 0;
   public Map<String, Integer> songOffsets = new HashMap<>();
   public boolean showLyrics = true;
   public LFConfig.LyricsStyle lyricsStyle = LFConfig.LyricsStyle.FLOATING;
   public boolean skyWords = false;
   public int airMode = 0;
   public boolean followView = false;
   public boolean viewLock = false;
   public boolean showUnderground = false;
   public boolean musicNotes = true;
   public boolean randomPosition = true;
   public boolean sentenceMode = false;
   public boolean allCaps = true;
   public boolean skyFollow = true;
   public float floatHeight = 0.0F;
   public int skyColor = 0;
   public int plainSkyColor = 0;
   public int customTop = -16777216;
   public int customHorizon = -16777216;
   public int customAurora1 = -1;
   public int customAurora2 = -1;
   public boolean customAurora = true;
   public boolean customStars = false;
   public boolean customMoon = false;
   public boolean customShooting = true;
   public int customWeather = 0;
   public boolean weatherRain = false;
   public boolean weatherSnow = false;
   public boolean weatherEmbers = false;
   public float lyricsScale = 1.0F;
   public float lyricsDistance = 1.0F;
   public float skyDistance = 150.0F;
   public int textShadow = 1;
   public int shadowColor = 0;
   public boolean varySizes = true;
   public boolean arcLayout = true;
   public int maxLines = 2;
   public String font = "poppins";
   public LFConfig.TextStyle textStyle = LFConfig.TextStyle.NEON;
   public String theme = "lavender";
   public boolean customTheme = false;
   public int singleColor = 0;
   public int customMain = -7178;
   public int customA = -42296;
   public int customB = -9447169;
   public boolean twoColor = true;
   public float twoColorStrength = 0.0F;
   public int twoColorColor = 0;
   public boolean glow = true;
   public boolean autoPerf = true;
   public int targetFps = 60;
   public int quality = 1;
   public int profile = 0;
   public boolean fpsBoost = false;
   public String boostBefore = "";
   public boolean fxSkyColor = true;
   public boolean fxWeather = true;
   public boolean fxAurora = true;
   public boolean fxStars = true;
   public String lastSky = "";
   public float textOpacity = 1.0F;
   public LFConfig.EnterAnim enterAnim = LFConfig.EnterAnim.NEON;
   public LFConfig.ExitAnim exitAnim = LFConfig.ExitAnim.FLOAT_UP;
   public float animSpeed = 1.0F;
   public boolean beatGlow = false;
   public boolean stars = true;
   public float starDensity = 0.6F;
   public boolean motes = false;
   public float moteDensity = 0.5F;
   public boolean streaks = true;
   public String skyPreset = "off";
   public int accentColor = 0;
   public int barColor = 0;
   public int starColor = 0;
   public int moteColor = 0;
   public int streakColor = 0;
   public float streakDensity = 0.5F;
   public boolean showTopBar = true;
   public boolean chroma = false;
   public boolean skyAlways = true;
   public int chromaColor = 0;
   public boolean customHotbar = false;
   public int hotbarColor = 0;
   public int hotbarBarColor = 0;
   public String cosmetic = "none";
   public LFConfig.TopBarStyle topBarStyle = LFConfig.TopBarStyle.KARAOKE;
   public boolean clock = false;
   public boolean ping = false;
   public boolean fps = false;
   public String topBarFont = "outfit";
   public float topBarScale = 1.0F;
   public float topBarX = 0.5F;
   public float topBarY = 0.02F;
   public float screenLyricsX = 0.5F;
   public float screenLyricsY = 0.32F;
   public boolean showOnlyWhenPlaying = true;
   public boolean hideWhenStopped = false;
   public boolean dimWhenIdle = true;
   public boolean mediaKeys = true;
   public boolean hideInScreenshots = true;
   public Map<String, String> profiles = new LinkedHashMap<>();

   public enum EnterAnim {
      WORD,
      FADE,
      RISE,
      NEON;
   }

   public enum ExitAnim {
      FLOAT_UP,
      DRIFT,
      FADE;
   }

   public enum LyricsStyle {
      SKY,
      FLOATING,
      SCREEN,
      WALL;
   }

   public enum Source {
      SPOTIFY,
      YOUTUBE,
      ANY;
   }

   public enum TextStyle {
      NEON,
      CLEAN;
   }

   public enum TopBarStyle {
      KARAOKE,
      SIMPLE;
   }
}
