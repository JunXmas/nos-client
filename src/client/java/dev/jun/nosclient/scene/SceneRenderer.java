package dev.jun.nosclient.scene;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class SceneRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("nosclient");
    public static final SceneRenderer INSTANCE = new SceneRenderer();
    private static final int ST = 14;
    private static final int SMS = 2048;
    private static final double FOV = Math.toRadians(52.0);
    private final SceneConfig cfg = SceneConfig.INSTANCE;
    private final GlState gls = new GlState();
    private boolean ready;
    private boolean failed;
    private int glChecks;
    private World world;
    private int nW;
    private int nNear;
    private int nWater;
    private int nCl;
    private int nSB;
    private SceneRenderer.Prog pw;
    private SceneRenderer.Prog ps;
    private SceneRenderer.Prog pp;
    private SceneRenderer.Prog psh;
    private SceneRenderer.Prog ppo;
    private SceneRenderer.Prog pcopy;
    private int vaoW;
    private int vaoWater;
    private int vaoP;
    private int bufP;
    private int vaoCl;
    private int bufCl;
    private int vaoS;
    private int bufS;
    private int vaoEmpty;
    private int txa;
    private int txw;
    private int txs;
    private int txm;
    private int fb;
    private int fbt;
    private int fbDepth;
    private int sfb;
    private int stx;
    private int mfb;
    private int mtx;
    private int mdt;
    private int outFbo;
    private Object outTex;
    private int tw = -1;
    private int th = -1;
    private int rw;
    private int rh;
    private boolean[] cloudMask;
    private int cShift = Integer.MIN_VALUE;
    private float[] sheepBuf;
    private FloatBuffer pd;
    private FloatBuffer sheepFb;
    private static final int PD_VERTS = 3600;
    private final float[] fe = new float[]{0.0F, 0.0F, -10000.0F, 1.0E9F, -10000.0F, 1.0E9F};
    private final double t0 = System.nanoTime() / 1.0E9;
    private double todD = this.cfg.tod;
    private double lastT;
    private static final int GRASS = 9551193;
    private static final int FOLIAGE = 7842607;
    private static final int BIRCH = 8431445;
    private static final int LILY = 2129968;
    private static final int WATER = 4159204;
    private static final Map<String, String> PATH = new HashMap<>();
    private static final Map<String, Integer> TINT;
    private static final int[] WL;
    private static final float[] I4;
    private static final float[] REFL;
    private static final double[][] SKY;
    private final List<SceneRenderer.Ev> ev = new ArrayList<>();
    private final List<SceneRenderer.P> sp = new ArrayList<>();
    private double petAcc;
    private static final double[][] FLY;
    private int pn;

    private SceneRenderer() {
        if (!this.cfg.blossom) {
            this.fe[2] = 1.0E9F;
            this.fe[3] = -10000.0F;
        }

        if (!this.cfg.cane) {
            this.fe[4] = 1.0E9F;
            this.fe[5] = -10000.0F;
        }
    }

    private double now() {
        return System.nanoTime() / 1.0E9 - this.t0 + 30.0;
    }

    public void toggle(int group, boolean on) {
        double n = this.now();
        int i = group * 2;
        if (on) {
            this.fe[i] = (float)n;
            this.fe[i + 1] = 1.0E9F;
            this.growFx(group, n);
        } else {
            this.fe[i + 1] = (float)n;
        }

        if (group == 1) {
            this.cfg.blossom = on;
        } else {
            this.cfg.cane = on;
        }

        this.cfg.save();
    }

    public boolean render() {
        if (this.failed) {
            return false;
        }

        this.gls.save();

        try {
            GlState.clean();
            if (!this.ready) {
                this.init();
            }

            this.frame(this.now());
            if (this.glChecks < 5 && ++this.glChecks == 5) {
                int e = GL33C.glGetError();
                if (e != 0) {
                    LOG.warn("Nos client cảnh nền: GL lỗi {}", e);
                } else {
                    LOG.info("Nos client cảnh nền: GL OK");
                }
            }

            return true;
        } catch (Throwable e) {
            this.failed = true;
            LOG.error("Nos client: cảnh nền hỏng, quay về panorama gốc", e);
            return false;
        } finally {
            this.gls.restore();
        }
    }

    private static int[][] img(ResourceManager rm, String path) throws IOException {
        try (
            InputStream in = rm.open(Identifier.withDefaultNamespace("textures/" + path + ".png"));
            NativeImage im = NativeImage.read(in);
        ) {
            int[][] p = new int[im.getHeight()][im.getWidth()];

            for (int y = 0; y < p.length; y++) {
                for (int x = 0; x < p[0].length; x++) {
                    p[y][x] = im.getPixel(x, y);
                }
            }

            return p;
        }
    }

    private static int tint(int argb, int rgb) {
        int a = argb >>> 24;
        int r = (argb >> 16 & 0xFF) * (rgb >> 16 & 0xFF) / 255;
        int g = (argb >> 8 & 0xFF) * (rgb >> 8 & 0xFF) / 255;
        int b = (argb & 0xFF) * (rgb & 0xFF) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int[][] tinted(int[][] p, int rgb) {
        for (int[] row : p) {
            for (int x = 0; x < row.length; x++) {
                row[x] = tint(row[x], rgb);
            }
        }

        return p;
    }

    private int[][] tile(ResourceManager rm, String n) throws IOException {
        if (!n.equals("gside")) {
            int[][] p = img(rm, PATH.get(n));
            Integer t = TINT.get(n);
            return t != null ? tinted(p, t) : p;
        }

        int[][] base = img(rm, "block/grass_block_side");
        int[][] over = tinted(img(rm, "block/grass_block_side_overlay"), 9551193);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int o = over[y][x];
                int b = base[y][x];
                int a = o >>> 24;
                int r = ((o >> 16 & 0xFF) * a + (b >> 16 & 0xFF) * (255 - a)) / 255;
                int g = ((o >> 8 & 0xFF) * a + (b >> 8 & 0xFF) * (255 - a)) / 255;
                int bl = ((o & 0xFF) * a + (b & 0xFF) * (255 - a)) / 255;
                base[y][x] = 0xFF000000 | r << 16 | g << 8 | bl;
            }
        }

        return base;
    }

    private static int upload(int[][] p, int w, int h, boolean repeat, boolean mip) {
        ByteBuffer b = MemoryUtil.memAlloc(w * h * 4);

        try {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = p[y][x];
                    b.put((byte)(c >> 16)).put((byte)(c >> 8)).put((byte)c).put((byte)(c >>> 24));
                }
            }

            b.flip();
            int t = GL33C.glGenTextures();
            GL33C.glBindTexture(3553, t);
            GL33C.glTexImage2D(3553, 0, 32856, w, h, 0, 6408, 5121, b);
            int wr = repeat ? 10497 : 33071;
            GL33C.glTexParameteri(3553, 10242, wr);
            GL33C.glTexParameteri(3553, 10243, wr);
            GL33C.glTexParameteri(3553, 10240, 9728);
            if (mip) {
                GL33C.glTexParameteri(3553, 33085, 4);
                GL33C.glGenerateMipmap(3553);
            }

            GL33C.glTexParameteri(3553, 10241, mip ? 9986 : 9728);
            return t;
        } finally {
            MemoryUtil.memFree(b);
        }
    }

    private static int vao(FloatBuffer data, int[] attrs, int stride, int usage, int[] bufOut) {
        int v = GL33C.glGenVertexArrays();
        GL33C.glBindVertexArray(v);
        int b = GL33C.glGenBuffers();
        GL33C.glBindBuffer(34962, b);
        GL33C.glBufferData(34962, data, usage);
        int off = 0;

        for (int i = 0; i < attrs.length; i++) {
            GL33C.glEnableVertexAttribArray(i);
            GL33C.glVertexAttribPointer(i, attrs[i], 5126, false, stride * 4, off * 4L);
            off += attrs[i];
        }

        if (bufOut != null) {
            bufOut[0] = b;
        }

        return v;
    }

    private static int vao(float[] data, int[] attrs, int stride, int usage, int[] bufOut) {
        FloatBuffer fb = MemoryUtil.memAllocFloat(Math.max(1, data.length));

        try {
            fb.put(data).flip();
            if (data.length == 0) {
                fb.limit(1);
            }

            return vao(fb, attrs, stride, usage, bufOut);
        } finally {
            MemoryUtil.memFree(fb);
        }
    }

    private void init() throws IOException {
        long t = System.nanoTime();
        int rl = GL33C.glGetInteger(3314);
        int sp = GL33C.glGetInteger(3316);
        int sr = GL33C.glGetInteger(3315);
        int al = GL33C.glGetInteger(3317);
        GL33C.glPixelStorei(3314, 0);
        GL33C.glPixelStorei(3316, 0);
        GL33C.glPixelStorei(3315, 0);
        GL33C.glPixelStorei(3317, 4);

        try {
            this.pw = new SceneRenderer.Prog(
                "#version 330 core\nlayout(location=0)in vec3 aP;layout(location=1)in vec2 aU;layout(location=2)in float aS;layout(location=3)in vec3 aV;layout(location=4)in vec4 aA;layout(location=5)in float aW;\nuniform mat4 uVP,uM;uniform float uT;uniform vec2 uF[3];out vec2 vU;out float vS;out vec3 vW,vO;\nfloat bo(float x){x=clamp(x,0.,1.);float y=x-1.;return 1.+2.70158*y*y*y+1.70158*y*y;}   // nảy như cubic-bezier(.34,1.56,.64,1) của intro\nvoid main(){vec3 p=aP;int f=int(aA.x+.5);\n if(f>0){float g=uT-uF[f].x-aA.y,k=aA.z,s;\n  s=k>1.5?bo(g/.35)*(1.-smoothstep(.9,1.1,g)):bo(g/(k>.5?.3:.4));      // 2 = mầm: hiện rồi nhường chỗ cho thân\n  s*=1.-smoothstep(0.,.28,uT-uF[f].y-aA.w);\n  vec3 c=(k>.5&&k<1.5)?vec3(mix(.6,1.,s),s,mix(.6,1.,s))*step(.001,s):vec3(s);   // 1 = mọc dọc từ chân khối\n  p=aV+(p-aV)*c;}\n float w=sin(uT*1.7+aV.x*.6+aV.z*.4)+.5*sin(uT*2.9+aV.z);p.x+=w*aW;p.z+=w*aW*.5;\n vec4 W=uM*vec4(p,1.);vW=W.xyz;vO=p;vU=aU;vS=aS;gl_Position=uVP*W;}\n",
                "#version 330 core\nin vec2 vU;in float vS;in vec3 vW,vO;\nuniform sampler2D uA,uWt,uR;uniform sampler2DShadow uSM;uniform mat4 uLVP;\nuniform vec3 uL,uFog,uCam,uCamO,uLd,uSunC,uAmb;uniform vec2 uFr,uRes;uniform float uT,uMode,uClip,uFrame,uFx;out vec4 o;\n// bóng đổ: shadow map 2048² nhìn từ mặt trời, lọc 3×3 (mỗi mẫu đã là 2×2 nhờ so sánh phần cứng)\nfloat shadow(vec3 n){vec4 q=uLVP*vec4(vO+n*.06,1.);vec3 s=q.xyz/q.w*.5+.5;\n if(s.x<0.||s.y<0.||s.x>1.||s.y>1.)return 1.;float r=0.;\n for(int i=-1;i<=1;i++)for(int j=-1;j<=1;j++)r+=texture(uSM,vec3(s.xy+vec2(i,j)/2048.,s.z-.0012));return r/9.;}\nvoid main(){if(uClip>.5&&vW.y>-.115)discard;\n float d=length(vW-uCam),fog=clamp((d-uFr.x)/(uFr.y-uFr.x),0.,1.);\n if(uMode>1.5){\n  float k=smoothstep(.55,1.,vS);vec3 lit=uL*.55+uSunC*.75,cl=uFx>.5?mix(mix(uFog,lit,.5)*.82,lit*1.08,k):uL*mix(.72,1.,k);   // đỉnh mây ăn nắng, đáy ăn màu trời\n  o=vec4(mix(cl,uFog,clamp((d-260.)/600.,0.,1.)*.7),1.);return;}\n if(uMode>.5){vec4 w=texture(uWt,vec2(vU.x,(vU.y+uFrame)/32.));                         // water_still: 32 khung\n  vec2 q=gl_FragCoord.xy/uRes+vec2(sin(uT*1.8+vW.z*2.3)*.0022,cos(uT*1.3+vW.x*1.7)*.0016);vec3 rf=texture(uR,q).rgb;\n  if(uFx>.5){vec3 V=normalize(uCam-vW),wn=normalize(vec3(sin(uT*1.8+vW.z*2.3+vW.x*.7)*.045,1.,cos(uT*1.3+vW.x*1.7)*.045));\n   float fr=.5+.5*pow(1.-max(dot(V,wn),0.),3.),sp=pow(max(dot(reflect(-uLd,wn),V),0.),220.)*shadow(vec3(0,1,0));\n   o=vec4(mix(mix(w.rgb*(uAmb+uSunC*.4)*.55,rf,fr),uFog,fog)+uSunC*sp*2.5,.94);return;}\n  o=vec4(mix(mix(w.rgb*uL,rf,.55),uFog,fog),.86);return;}\n vec4 c=texture(uA,vU);if(c.a<.5)discard;vec3 col;\n if(uFx>.5){vec3 n=normalize(cross(dFdx(vO),dFdy(vO)));if(dot(n,uCamO-vO)<0.)n=-n;   // pháp tuyến phẳng từ đạo hàm màn hình, quay về phía mắt\n  if(vS>.85&&vS<.95)n=normalize(n*.25+vec3(0,1,0));                                    // cây cỏ hai mặt chéo: coi như hướng lên\n  float nl=max(dot(n,uLd),0.),sh=nl>0.?shadow(n):0.;\n  col=c.rgb*(uAmb*vS+uSunC*nl*sh*(.6+.4*vS));}\n else col=c.rgb*vS*uL;\n o=vec4(mix(col,uFog,fog),1.);}\n"
            );
            this.ps = new SceneRenderer.Prog(
                "#version 330 core\nout vec2 vP;void main(){vec2 p=vec2(gl_VertexID==1?3.:-1.,gl_VertexID==2?3.:-1.);vP=p;gl_Position=vec4(p,.9999,1.);}\n",
                "#version 330 core\nin vec2 vP;uniform vec3 uFw,uRt,uUp,uZen,uHor,uSun,uGlow;uniform vec2 uTan;uniform float uFlip,uNight,uT;out vec4 o;\nvoid main(){vec3 d=normalize(uFw+vP.x*uTan.x*uRt+vP.y*uTan.y*uUp);d.y*=uFlip;\n vec3 c=d.y>0.?mix(uHor,uZen,pow(min(d.y*2.,1.),.6)):uHor;\n float s=max(dot(d,uSun),0.);c+=uGlow*(pow(s,9.)*.35+pow(s,60.)*.45+pow(s,600.)*3.);\n if(uNight>0.&&d.y>0.){vec3 q=floor(d*160.);float h=fract(sin(dot(q,vec3(12.9898,78.233,37.719)))*43758.5453);\n  if(h>.997)c+=uNight*(.55+.45*sin(uT*1.5+h*400.))*smoothstep(0.,.15,d.y);}\n o=vec4(c,1.);}\n"
            );
            this.pp = new SceneRenderer.Prog(
                "#version 330 core\nlayout(location=0)in vec3 aC;layout(location=1)in vec2 aK;layout(location=2)in vec2 aU;layout(location=3)in vec4 aCol;\nuniform mat4 uV,uP;out vec2 vU;out vec4 vC;void main(){vec4 v=uV*vec4(aC,1.);v.xy+=aK;gl_Position=uP*v;vU=aU;vC=aCol;}\n",
                "#version 330 core\nin vec2 vU;in vec4 vC;uniform sampler2D uA;out vec4 o;void main(){vec4 c=texture(uA,vU)*vC;if(c.a<.04)discard;o=c;}\n"
            );
            this.psh = new SceneRenderer.Prog(
                "#version 330 core\nlayout(location=0)in vec3 aP;layout(location=1)in vec2 aU;layout(location=2)in float aS;layout(location=3)in vec3 aV;layout(location=4)in vec4 aA;layout(location=5)in float aW;\nuniform mat4 uVP,uM;uniform float uT;uniform vec2 uF[3];out vec2 vU;out float vS;out vec3 vW,vO;\nfloat bo(float x){x=clamp(x,0.,1.);float y=x-1.;return 1.+2.70158*y*y*y+1.70158*y*y;}   // nảy như cubic-bezier(.34,1.56,.64,1) của intro\nvoid main(){vec3 p=aP;int f=int(aA.x+.5);\n if(f>0){float g=uT-uF[f].x-aA.y,k=aA.z,s;\n  s=k>1.5?bo(g/.35)*(1.-smoothstep(.9,1.1,g)):bo(g/(k>.5?.3:.4));      // 2 = mầm: hiện rồi nhường chỗ cho thân\n  s*=1.-smoothstep(0.,.28,uT-uF[f].y-aA.w);\n  vec3 c=(k>.5&&k<1.5)?vec3(mix(.6,1.,s),s,mix(.6,1.,s))*step(.001,s):vec3(s);   // 1 = mọc dọc từ chân khối\n  p=aV+(p-aV)*c;}\n float w=sin(uT*1.7+aV.x*.6+aV.z*.4)+.5*sin(uT*2.9+aV.z);p.x+=w*aW;p.z+=w*aW*.5;\n vec4 W=uM*vec4(p,1.);vW=W.xyz;vO=p;vU=aU;vS=aS;gl_Position=uVP*W;}\n",
                "#version 330 core\nin vec2 vU;uniform sampler2D uA;out vec4 o;void main(){if(texture(uA,vU).a<.5)discard;o=vec4(1);}\n"
            );
            this.ppo = new SceneRenderer.Prog(
                "#version 330 core\nout vec2 vP;void main(){vec2 p=vec2(gl_VertexID==1?3.:-1.,gl_VertexID==2?3.:-1.);vP=p;gl_Position=vec4(p,.9999,1.);}\n",
                "#version 330 core\nin vec2 vP;uniform sampler2D uC,uD;uniform vec2 uSun;uniform float uSunV;uniform vec3 uRay;uniform float uExp;out vec4 o;\nvec3 aces(vec3 x){return clamp(x*(2.51*x+.03)/(x*(2.43*x+.59)+.14),0.,1.);}\nvoid main(){vec2 uv=vP*.5+.5,px=1./vec2(textureSize(uC,0));vec3 c=textureLod(uC,uv,0.).rgb;\n vec3 nb=(textureLod(uC,uv+vec2(px.x,0),0.).rgb+textureLod(uC,uv-vec2(px.x,0),0.).rgb+textureLod(uC,uv+vec2(0,px.y),0.).rgb+textureLod(uC,uv-vec2(0,px.y),0.).rgb)*.25;\n c+=(c-nb)*.45;\n vec3 bl=textureLod(uC,uv,3.).rgb*.3+textureLod(uC,uv,5.).rgb*.4+textureLod(uC,uv,6.5).rgb*.3;c+=max(bl-.85,0.)*.9;\n if(uSunV>0.){vec2 dd=(uSun-uv)/32.,p=uv;float r=0.,w=1.;\n  for(int i=0;i<32;i++){p+=dd;float sk=step(.99999,textureLod(uD,p,0.).r);r+=sk*max(dot(textureLod(uC,p,1.).rgb,vec3(.33))-.62,0.)*w*2.;w*=.95;}\n  float fall=exp(-length((uv-uSun)*vec2(1.78,1.))*2.2),sk0=step(.99999,textureLod(uD,uv,0.).r);c+=uRay*(r/10.)*uSunV*fall*mix(1.,.35,sk0);}\n float isSky=step(.99999,textureLod(uD,uv,0.).r);c=mix(pow(aces(pow(max(c,0.),vec3(2.2))*uExp),vec3(1./2.2)),min(c,1.),isSky);float l=dot(c,vec3(.2126,.7152,.0722));c=max(mix(vec3(l),c,1.04),0.);\n vec2 q=uv-.5;o=vec4(c*(1.-dot(q,q)*.5),1.);}\n"
            );
            this.pcopy = new SceneRenderer.Prog(
                "#version 330 core\nout vec2 vP;void main(){vec2 p=vec2(gl_VertexID==1?3.:-1.,gl_VertexID==2?3.:-1.);vP=p;gl_Position=vec4(p,.9999,1.);}\n",
                "#version 330 core\nin vec2 vP;uniform sampler2D uC;out vec4 o;void main(){o=vec4(textureLod(uC,vP*.5+.5,0.).rgb,1.);}\n"
            );
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            int[][] atlas = new int[256][256];

            for (int i = 0; i < World.TILES.length; i++) {
                int[][] p = this.tile(rm, World.TILES[i]);
                int x0 = i % 16 * 16;
                int y0 = i / 16 * 16;
                int s = World.tileSize(World.TILES[i]);

                for (int y = 0; y < s && y < p.length; y++) {
                    for (int x = 0; x < s && x < p[0].length; x++) {
                        atlas[y0 + y][x0 + x] = p[y][x];
                    }
                }
            }

            blit(atlas, img(rm, "entity/sheep/sheep"), 0, 192);
            blit(atlas, img(rm, "entity/sheep/sheep_wool"), 64, 192);
            this.txa = upload(atlas, 256, 256, false, true);
            if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                GL33C.glTexParameterf(3553, 34046, 8.0F);
            }

            int[][] water = tinted(img(rm, "block/water_still"), 4159204);
            this.txw = upload(water, water[0].length, water.length, true, false);
            int[][] sun = img(rm, "environment/celestial/sun");
            int[][] moon = img(rm, "environment/celestial/moon/full_moon");
            this.txs = upload(sun, sun[0].length, sun.length, false, false);
            this.txm = upload(moon, moon[0].length, moon.length, false, false);
            int[][] cl = img(rm, "environment/clouds");
            this.cloudMask = new boolean[65536];

            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) {
                    this.cloudMask[y * 256 + x] = cl[y % cl.length][x % cl[0].length] >>> 24 > 127;
                }
            }
        } finally {
            GL33C.glPixelStorei(3314, rl);
            GL33C.glPixelStorei(3316, sp);
            GL33C.glPixelStorei(3315, sr);
            GL33C.glPixelStorei(3317, al);
        }

        this.world = new World();
        World.Mesh var19 = this.world.build();
        this.nW = var19.land().length / 14;
        this.nNear = var19.nearVerts();
        this.nWater = var19.water().length / 14;
        int[] var20 = new int[1];
        this.vaoW = vao(var19.land(), WL, 14, 35044, null);
        this.vaoWater = vao(var19.water(), WL, 14, 35044, null);
        this.pd = MemoryUtil.memAllocFloat(39600);
        this.vaoP = vao(this.pd, new int[]{3, 2, 2, 4}, 11, 35048, var20);
        this.bufP = var20[0];
        this.vaoCl = vao(new float[14], WL, 14, 35048, var20);
        this.bufCl = var20[0];
        this.sheepBuf = new float[World.SHEEP_FLOATS];
        this.sheepFb = MemoryUtil.memAllocFloat(World.SHEEP_FLOATS);
        this.vaoS = vao(this.sheepFb, WL, 14, 35048, var20);
        this.bufS = var20[0];
        this.vaoEmpty = GL33C.glGenVertexArrays();
        this.stx = depthTex(2048, 2048, true);
        this.sfb = GL33C.glGenFramebuffers();
        GL33C.glBindFramebuffer(36160, this.sfb);
        GL33C.glFramebufferTexture2D(36160, 36096, 3553, this.stx, 0);
        GL33C.glDrawBuffer(0);
        GL33C.glReadBuffer(0);
        check("shadow");
        this.ready = true;
        LOG.info("Nos client cảnh nền: dựng xong {} đỉnh trong {} ms", this.nW, (System.nanoTime() - t) / 1000000L);
    }

    private static void blit(int[][] dst, int[][] src, int x0, int y0) {
        for (int y = 0; y < src.length; y++) {
            System.arraycopy(src[y], 0, dst[y0 + y], x0, src[y].length);
        }
    }

    private static int depthTex(int w, int h, boolean cmp) {
        int t = GL33C.glGenTextures();
        GL33C.glBindTexture(3553, t);
        GL33C.glTexImage2D(3553, 0, 33190, w, h, 0, 6402, 5125, (ByteBuffer)null);
        int f = cmp ? 9729 : 9728;
        GL33C.glTexParameteri(3553, 10241, f);
        GL33C.glTexParameteri(3553, 10240, f);
        GL33C.glTexParameteri(3553, 10242, 33071);
        GL33C.glTexParameteri(3553, 10243, 33071);
        if (cmp) {
            GL33C.glTexParameteri(3553, 34892, 34894);
            GL33C.glTexParameteri(3553, 34893, 515);
        }

        return t;
    }

    private static void check(String what) {
        int s = GL33C.glCheckFramebufferStatus(36160);
        if (s != 36053) {
            throw new IllegalStateException("FBO " + what + " chưa đủ: 0x" + Integer.toHexString(s));
        }
    }

    private void targets(RenderTarget main) {
        int w = main.width;
        int h = main.height;
        GlTexture color = (GlTexture)main.getColorTexture();
        if (w != this.tw || h != this.th) {
            if (this.tw > 0) {
                GL33C.glDeleteFramebuffers(new int[]{this.fb, this.mfb});
                GL33C.glDeleteTextures(new int[]{this.fbt, this.mtx, this.mdt});
                GL33C.glDeleteRenderbuffers(this.fbDepth);
            }

            this.tw = w;
            this.th = h;
            this.rw = Math.max(1, w / 2);
            this.rh = Math.max(1, h / 2);
            this.fbt = GL33C.glGenTextures();
            GL33C.glBindTexture(3553, this.fbt);
            GL33C.glTexImage2D(3553, 0, 32856, this.rw, this.rh, 0, 6408, 5121, (ByteBuffer)null);
            GL33C.glTexParameteri(3553, 10241, 9729);
            GL33C.glTexParameteri(3553, 10240, 9729);
            GL33C.glTexParameteri(3553, 10242, 33071);
            GL33C.glTexParameteri(3553, 10243, 33071);
            this.fb = GL33C.glGenFramebuffers();
            GL33C.glBindFramebuffer(36160, this.fb);
            GL33C.glFramebufferTexture2D(36160, 36064, 3553, this.fbt, 0);
            this.fbDepth = GL33C.glGenRenderbuffers();
            GL33C.glBindRenderbuffer(36161, this.fbDepth);
            GL33C.glRenderbufferStorage(36161, 33190, this.rw, this.rh);
            GL33C.glFramebufferRenderbuffer(36160, 36096, 36161, this.fbDepth);
            check("reflection");
            this.mtx = GL33C.glGenTextures();
            GL33C.glBindTexture(3553, this.mtx);
            GL33C.glTexImage2D(3553, 0, 32856, w, h, 0, 6408, 5121, (ByteBuffer)null);
            GL33C.glTexParameteri(3553, 10241, 9987);
            GL33C.glTexParameteri(3553, 10240, 9729);
            GL33C.glTexParameteri(3553, 10242, 33071);
            GL33C.glTexParameteri(3553, 10243, 33071);
            GL33C.glGenerateMipmap(3553);
            this.mdt = depthTex(w, h, false);
            this.mfb = GL33C.glGenFramebuffers();
            GL33C.glBindFramebuffer(36160, this.mfb);
            GL33C.glFramebufferTexture2D(36160, 36064, 3553, this.mtx, 0);
            GL33C.glFramebufferTexture2D(36160, 36096, 3553, this.mdt, 0);
            check("scene");
        }

        if (color != this.outTex) {
            if (this.outFbo == 0) {
                this.outFbo = GL33C.glGenFramebuffers();
            }

            GL33C.glBindFramebuffer(36160, this.outFbo);
            GL33C.glFramebufferTexture2D(36160, 36064, 3553, color.glId(), 0);
            check("out");
            this.outTex = color;
        }
    }

    private static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];

        for (int c = 0; c < 4; c++) {
            for (int i = 0; i < 4; i++) {
                r[c * 4 + i] = a[i] * b[c * 4] + a[4 + i] * b[c * 4 + 1] + a[8 + i] * b[c * 4 + 2] + a[12 + i] * b[c * 4 + 3];
            }
        }

        return r;
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return new double[]{a[0] / l, a[1] / l, a[2] / l};
    }

    private static double[] add(double[] a, double[] b, double k) {
        return new double[]{a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k};
    }

    private static SceneRenderer.Look lookAt(double[] e, double[] t) {
        double[] fw = norm(sub(t, e));
        double[] rt = norm(cross(fw, new double[]{0.0, 1.0, 0.0}));
        double[] up = cross(rt, fw);
        float[] v = new float[]{
            (float)rt[0],
            (float)up[0],
            (float)(-fw[0]),
            0.0F,
            (float)rt[1],
            (float)up[1],
            (float)(-fw[1]),
            0.0F,
            (float)rt[2],
            (float)up[2],
            (float)(-fw[2]),
            0.0F,
            (float)(-dot(rt, e)),
            (float)(-dot(up, e)),
            (float)dot(fw, e),
            1.0F
        };
        return new SceneRenderer.Look(v, fw, rt, up);
    }

    private static float[] ortho(double h, double n, double f) {
        return new float[]{
            (float)(1.0 / h),
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            (float)(1.0 / h),
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            (float)(-2.0 / (f - n)),
            0.0F,
            0.0F,
            0.0F,
            (float)(-(f + n) / (f - n)),
            1.0F
        };
    }

    private static double lerp(double a, double b, double k) {
        return a + (b - a) * k;
    }

    private static double clamp01(double v) {
        return Math.min(1.0, Math.max(0.0, v));
    }

    private static double[] mix3(double[] a, double[] b, double k) {
        return new double[]{lerp(a[0], b[0], k), lerp(a[1], b[1], k), lerp(a[2], b[2], k)};
    }

    private static double[] scale3(double[] a, double k) {
        return new double[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static double[][] skyAt(double v) {
        for (int i = 1; i < SKY.length; i++) {
            if (v <= SKY[i][0]) {
                double[] a = SKY[i - 1];
                double[] b = SKY[i];
                double k = (v - a[0]) / (b[0] - a[0]);
                double[][] r = new double[3][3];

                for (int c = 0; c < 3; c++) {
                    for (int j = 0; j < 3; j++) {
                        r[c][j] = Math.round(lerp(a[1 + c * 3 + j], b[1 + c * 3 + j], k)) / 255.0;
                    }
                }

                return r;
            }
        }

        double[] l = SKY[SKY.length - 1];
        return new double[][]{
            {l[1] / 255.0, l[2] / 255.0, l[3] / 255.0}, {l[4] / 255.0, l[5] / 255.0, l[6] / 255.0}, {l[7] / 255.0, l[8] / 255.0, l[9] / 255.0}
        };
    }

    private static double[] az(double a, double el) {
        double c = Math.cos(a);
        double s = Math.sin(a);
        double e = Math.toRadians(el);
        return new double[]{-s * Math.cos(e), Math.sin(e), -c * Math.cos(e)};
    }

    private static void u3(SceneRenderer.Prog p, String n, double[] v) {
        GL33C.glUniform3f(p.u(n), (float)v[0], (float)v[1], (float)v[2]);
    }

    private void growFx(int group, double t) {
        if (this.world != null) {
            if (group == 1) {
                for (World.Tree tr : this.world.trees) {
                    double[] b = new double[]{tr.x() + 0.5, tr.y() + 0.2, tr.z() + 0.5};
                    double a = t + tr.t0();
                    this.ev.add(new SceneRenderer.Ev(a + 0.4, () -> {
                        SceneRenderer.P p = new SceneRenderer.P();
                        p.fly = true;
                        p.b = b;
                        p.max = 0.45;
                        p.tile = "bone";
                        this.sp.add(p);
                    }));

                    for (double k : new double[]{0.85, 1.05}) {
                        this.ev.add(new SceneRenderer.Ev(a + k, () -> this.burst(b, 12)));
                    }
                }
            } else {
                for (double[] c : this.world.canes) {
                    this.ev.add(new SceneRenderer.Ev(t + c[3], () -> this.burst(new double[]{c[0] + 0.5, c[1] + 0.2, c[2] + 0.5}, 6)));
                }
            }
        }
    }

    private void burst(double[] at, int n) {
        ThreadLocalRandom r = ThreadLocalRandom.current();

        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * 6.283;
            double rr = 0.15 + r.nextDouble() * 0.5;
            SceneRenderer.P p = new SceneRenderer.P();
            p.x = at[0] + Math.cos(a) * rr;
            p.y = at[1] + r.nextDouble() * 0.6;
            p.z = at[2] + Math.sin(a) * rr;
            p.vy = 0.5 + r.nextDouble() * 0.5;
            p.max = 0.7 + r.nextDouble() * 0.4;
            p.tile = "glint";
            p.sz = 0.16;
            p.glow = true;
            this.sp.add(p);
        }
    }

    private void stepFx(double t, double dt) {
        List<SceneRenderer.Ev> due = new ArrayList<>();
        Iterator<SceneRenderer.Ev> it = this.ev.iterator();

        while (it.hasNext()) {
            SceneRenderer.Ev e = it.next();
            if (e.at <= t) {
                it.remove();
                due.add(e);
            }
        }

        for (SceneRenderer.Ev e : due) {
            e.fn.run();
        }

        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (this.cfg.blossom && t > this.fe[2] + 4.0F) {
            this.petAcc += dt * 16.0;

            while (this.petAcc > 1.0 && this.sp.size() < 260) {
                this.petAcc--;
                World.Tree tr = this.world.trees.get(r.nextInt(this.world.trees.size()));
                int[] l = tr.leaves().get(r.nextInt(tr.leaves().size()));
                SceneRenderer.P p = new SceneRenderer.P();
                p.x = l[0] + r.nextDouble();
                p.y = l[1] + r.nextDouble() * 0.4;
                p.z = l[2] + r.nextDouble();
                p.vx = 0.25;
                p.vy = -(0.5 + r.nextDouble() * 0.35);
                p.vz = 0.05;
                p.ph = r.nextDouble() * 6.28;
                p.max = 9.0;
                p.tile = "p" + r.nextInt(12);
                p.sz = 0.09;
                p.pet = true;
                this.sp.add(p);
            }
        }

        Iterator<SceneRenderer.P> itx = this.sp.iterator();

        while (itx.hasNext()) {
            SceneRenderer.P p = itx.next();
            p.life += dt;
            if (p.life > p.max) {
                itx.remove();
            } else if (!p.fly) {
                if (p.pet) {
                    if (p.land < 0.0) {
                        p.x = p.x + (p.vx + Math.sin(t * 1.7 + p.ph) * 0.35) * dt;
                        p.z = p.z + (p.vz + Math.cos(t * 1.3 + p.ph) * 0.2) * dt;
                        p.y = p.y + p.vy * dt;
                        int cx = (int)Math.floor(p.x);
                        int cz = (int)Math.floor(p.z);
                        double g = World.isW(cx, cz) ? -0.115 : this.world.hgt(cx, cz) + 0.01;
                        if (p.y <= g) {
                            p.y = g;
                            p.land = p.life;
                            p.max = p.life + 4.0;
                        }
                    } else if (World.isW((int)Math.floor(p.x), (int)Math.floor(p.z))) {
                        p.x += 0.06 * dt;
                    }
                } else {
                    p.x = p.x + p.vx * dt;
                    p.y = p.y + p.vy * dt;
                    p.z = p.z + p.vz * dt;
                }
            }
        }
    }

    private void put(double[] c, double s, String tile, double r, double g, double b, double a) {
        float[] u = World.uv(tile);
        float[][] q = new float[][]{
            {-1.0F, 1.0F, u[0], u[1]},
            {1.0F, 1.0F, u[2], u[1]},
            {1.0F, -1.0F, u[2], u[3]},
            {-1.0F, 1.0F, u[0], u[1]},
            {1.0F, -1.0F, u[2], u[3]},
            {-1.0F, -1.0F, u[0], u[3]}
        };

        for (float[] k : q) {
            if (this.pn >= 3588) {
                return;
            }

            this.pd
                .put((float)c[0])
                .put((float)c[1])
                .put((float)c[2])
                .put((float)(k[0] * s))
                .put((float)(k[1] * s))
                .put(k[2])
                .put(k[3])
                .put((float)r)
                .put((float)g)
                .put((float)b)
                .put((float)a);
            this.pn++;
        }
    }

    private int[] fillSprites(double t, float[] V, double[] cam, double[] lt, double night) {
        this.pd.clear();
        this.pn = 0;
        double[] R = new double[]{V[0], V[4], V[8]};
        double[] U = new double[]{V[1], V[5], V[9]};
        double[] F = new double[]{-V[2], -V[6], -V[10]};

        for (SceneRenderer.P p : this.sp) {
            if (p.fly) {
                double k = Math.min(1.0, p.life / p.max);
                double e = k * k;
                double[] s = add(add(add(cam, R, 1.8), U, -1.2), F, 4.0);
                double[] mid = new double[]{(s[0] + p.b[0]) / 2.0, Math.max(s[1], p.b[1]) + 1.4, (s[2] + p.b[2]) / 2.0};
                double[] c = new double[3];

                for (int i = 0; i < 3; i++) {
                    c[i] = (1.0 - e) * (1.0 - e) * s[i] + 2.0 * (1.0 - e) * e * mid[i] + e * e * p.b[i];
                }

                this.put(c, lerp(0.32, 0.12, k), "bone", lt[0], lt[1], lt[2], 1.0);
            } else {
                double k = p.life / p.max;
                double a = p.pet ? (p.land < 0.0 ? 1.0 : Math.max(0.0, 1.0 - (p.life - p.land) / 4.0)) : 1.0 - k * k;
                double[] c = new double[]{p.x, p.y, p.z};
                if (p.glow) {
                    this.put(c, p.sz * lerp(1.2, 0.5, k), p.tile, 1.0, 1.0, 1.0, a);
                } else {
                    this.put(c, p.sz, p.tile, lt[0], lt[1], lt[2], a);
                }
            }
        }

        int nN = this.pn;
        if (night > 0.15) {
            for (double[] f : FLY) {
                double b = Math.max(0.0, Math.sin(t * f[4] * 3.0 + f[3])) * night;
                if (!(b < 0.05)) {
                    this.put(
                        new double[]{
                            f[0] + Math.sin(t * f[4] + f[3]) * 1.2, f[1] + Math.cos(t * f[4] * 0.8 + f[3]) * 0.5, f[2] + Math.cos(t * f[4] * 0.6 + f[3]) * 1.2
                        },
                        0.09,
                        "fly",
                        0.85 * b,
                        b,
                        0.5 * b,
                        1.0
                    );
                }
            }
        }

        return new int[]{nN, this.pn - nN};
    }

    private void frame(double t) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main.width > 0 && main.height > 0) {
            this.targets(main);
            boolean FX = this.cfg.fx;
            double dt = Math.min(0.05, Math.max(0.0, t - this.lastT));
            this.lastT = t;
            this.todD = this.todD + (this.cfg.tod - this.todD) * (1.0 - Math.exp(-dt * 2.2));
            double v = this.todD;
            double night = Math.max(0.0, (v - 55.0) / 45.0);
            double[][] sk = skyAt(v);
            double[] zen0 = sk[0];
            double[] hor0 = sk[1];
            double[] lt = sk[2];
            double dayK = FX ? (1.0 - night) * (1.0 - Math.exp(-Math.pow((v - 47.0) / 16.0, 2.0))) : 0.0;
            double gk = FX ? Math.exp(-Math.pow((v - 47.0) / 11.0, 2.0)) : 0.0;
            double[] zen1 = FX ? new double[]{zen0[0] * lerp(1.0, 0.5, dayK), zen0[1] * lerp(1.0, 0.68, dayK), zen0[2] * lerp(1.0, 0.97, dayK)} : zen0;
            double[] zen = mix3(zen1, new double[]{0.56, 0.5, 0.74}, gk * 0.85);
            double[] hor1 = FX ? new double[]{hor0[0] * lerp(1.0, 0.74, dayK), hor0[1] * lerp(1.0, 0.86, dayK), hor0[2]} : hor0;
            double[] hor = mix3(hor1, new double[]{1.0, 0.7, 0.52}, gk * 0.7);
            double dl = Math.sin(t * 0.035);
            double[] T0 = new double[]{1.0 + dl * 2.0, 6.0, -70.0};
            double[] cam = new double[]{1.0 + dl * 3.5, 2.6 + Math.sin(t * 0.05) * 0.1, 3.2 - Math.cos(t * 0.035) * 0.5};
            SceneRenderer.Look L = lookAt(cam, T0);
            double aspect = (double)this.tw / this.th;
            double TY = Math.tan(FOV / 2.0);
            double TX = TY * aspect;
            double NEAR = 0.1;
            double FAR = 600.0;
            float[] PROJ = new float[]{
                (float)(1.0 / TX),
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                (float)(1.0 / TY),
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                (float)((FAR + NEAR) / (NEAR - FAR)),
                -1.0F,
                0.0F,
                0.0F,
                (float)(2.0 * FAR * NEAR / (NEAR - FAR)),
                0.0F
            };
            float[] VIEW = L.view();
            float[] VP = mul(PROJ, VIEW);
            double sEl = 20.0 - v * 0.42;
            double mEl = (v - 56.0) * 0.4;
            double[] sunD = az(-0.06, sEl);
            double[] moonD = az(0.14, mEl);
            double glow = Math.exp(-Math.pow((v - 47.0) / 13.0, 2.0));
            double[] sunCol = scale3(new double[]{1.0, lerp(0.95, 0.62, glow), lerp(0.85, 0.4, glow)}, clamp01((sEl + 5.0) / 5.0));
            double[] moonCol = scale3(new double[]{0.9, 0.92, 1.0}, clamp01((mEl + 3.0) / 5.0));
            double k47 = Math.min(1.0, v / 47.0);
            double sunI = clamp01((sEl + 4.5) / 5.5);
            double mI = clamp01((mEl + 2.0) / 6.0) * 0.3;
            boolean useSun = sunI >= mI;
            double[] Ld = useSun
                ? az(lerp(-1.45, -0.06, k47 * k47), Math.max(7.0, sEl > 0.0 ? Math.min(58.0, sEl * 3.4) : sEl))
                : az(0.14, Math.max(14.0, mEl * 1.5));
            double[] warm = new double[]{1.0, lerp(0.93, 0.62, glow), lerp(0.84, 0.4, glow)};
            double[] sunC = useSun ? scale3(warm, 1.25 * sunI) : scale3(new double[]{0.55, 0.65, 1.0}, mI);
            double[] amb = new double[3];

            for (int i = 0; i < 3; i++) {
                amb[i] = lt[i] * (0.3 + 0.12 * night) + lerp(lt[i], zen[i], 0.35) * 0.12;
            }

            float[] LVP = I4;
            if (FX) {
                double[] C = new double[]{2.0, 4.0, -9.0};
                LVP = mul(ortho(52.0, 1.0, 300.0), lookAt(add(C, Ld, 140.0), C).view());
            }

            this.stepFx(t, dt);
            this.nSB = this.world.buildSheep(this.sheepBuf, t);
            this.sheepFb.clear();
            this.sheepFb.put(this.sheepBuf, 0, this.nSB * 14).flip();
            GL33C.glBindBuffer(34962, this.bufS);
            GL33C.glBufferSubData(34962, 0L, this.sheepFb);
            int[] na = this.fillSprites(t, VIEW, cam, lt, night);
            int nN = na[0];
            int nA = na[1];
            this.pd.flip();
            GL33C.glBindBuffer(34962, this.bufP);
            GL33C.glBufferSubData(34962, 0L, this.pd);
            double drift = t * 0.5;
            int csh = (int)Math.floor(drift / 12.0);
            if (csh != this.cShift) {
                float[] cl = World.buildClouds(this.cloudMask, csh);
                this.nCl = cl.length / 14;
                FloatBuffer b = MemoryUtil.memAllocFloat(Math.max(1, cl.length));

                try {
                    b.put(cl).flip();
                    GL33C.glBindBuffer(34962, this.bufCl);
                    GL33C.glBufferData(34962, b, 35048);
                } finally {
                    MemoryUtil.memFree(b);
                }

                this.cShift = csh;
            }

            float[] TRc = new float[]{1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, (float)(drift - csh * 12), 0.0F, 0.0F, 1.0F};
            double[] fogC = FX ? mix3(hor, zen, lerp(0.3, 0.45, glow)) : hor;
            int fr = (int)(Math.floor(t * 10.0) % 32.0);
            GL33C.glEnable(2929);
            if (FX) {
                GL33C.glBindFramebuffer(36160, this.sfb);
                GL33C.glViewport(0, 0, 2048, 2048);
                GL33C.glClear(256);
                GL33C.glEnable(32823);
                GL33C.glPolygonOffset(2.0F, 4.0F);
                GL33C.glUseProgram(this.psh.id);
                GL33C.glUniformMatrix4fv(this.psh.u("uVP"), false, LVP);
                GL33C.glUniformMatrix4fv(this.psh.u("uM"), false, I4);
                GL33C.glUniform1f(this.psh.u("uT"), (float)t);
                GL33C.glUniform2fv(this.psh.u("uF"), this.fe);
                GL33C.glUniform1i(this.psh.u("uA"), 0);
                GL33C.glActiveTexture(33984);
                GL33C.glBindTexture(3553, this.txa);
                GL33C.glBindVertexArray(this.vaoW);
                GL33C.glDrawArrays(4, 0, this.nNear);
                GL33C.glBindVertexArray(this.vaoS);
                GL33C.glDrawArrays(4, 0, this.nSB);
                GL33C.glDisable(32823);
            }

            GL33C.glActiveTexture(33987);
            GL33C.glBindTexture(3553, this.stx);
            GL33C.glActiveTexture(33986);
            GL33C.glBindTexture(3553, 0);
            GL33C.glActiveTexture(33985);
            GL33C.glBindTexture(3553, this.txw);
            GL33C.glActiveTexture(33984);
            float[] LVPf = LVP;
            int nAll = nN + nA;
            Runnable[] skyPass = new Runnable[2];

            for (int fi = 0; fi < 2; fi++) {
                float flip = fi == 0 ? -1.0F : 1.0F;
                skyPass[fi] = () -> {
                    GL33C.glDisable(2929);
                    GL33C.glUseProgram(this.ps.id);
                    u3(this.ps, "uFw", L.fw());
                    u3(this.ps, "uRt", L.rt());
                    u3(this.ps, "uUp", L.up());
                    GL33C.glUniform2f(this.ps.u("uTan"), (float)TX, (float)TY);
                    GL33C.glUniform1f(this.ps.u("uFlip"), flip);
                    u3(this.ps, "uZen", zen);
                    u3(this.ps, "uHor", hor);
                    u3(this.ps, "uSun", sunD);
                    u3(this.ps, "uGlow", FX ? scale3(warm, (0.05 + 0.75 * glow) * sunI) : new double[]{glow * 0.75, glow * 0.36, glow * 0.12});
                    GL33C.glUniform1f(this.ps.u("uNight"), (float)night);
                    GL33C.glUniform1f(this.ps.u("uT"), (float)t);
                    GL33C.glBindVertexArray(this.vaoEmpty);
                    GL33C.glDrawArrays(4, 0, 3);
                    GL33C.glUseProgram(this.pp.id);
                    GL33C.glUniformMatrix4fv(this.pp.u("uV"), false, VIEW);
                    GL33C.glUniformMatrix4fv(this.pp.u("uP"), false, PROJ);
                    GL33C.glUniform1i(this.pp.u("uA"), 0);
                    GL33C.glEnable(3042);
                    GL33C.glBlendFunc(1, 1);
                    GL33C.glBindVertexArray(this.vaoP);
                    GL33C.glBindBuffer(34962, this.bufP);
                    Object[][] bodies = new Object[][]{{sunD, sunCol, this.txs, lerp(18.0, 24.0, glow)}, {moonD, moonCol, this.txm, 15.0}};

                    for (Object[] bd : bodies) {
                        double[] d = (double[])bd[0];
                        double[] c = (double[])bd[1];
                        if (!(c[0] <= 0.0)) {
                            double s = (Double)bd[3];
                            double[] p = add(cam, new double[]{d[0], d[1] * flip, d[2]}, 300.0);
                            GL33C.glBindTexture(3553, (Integer)bd[2]);
                            float[] a = new float[66];
                            float[][] qx = new float[][]{
                                {-1.0F, 1.0F, 0.0F, 0.0F},
                                {1.0F, 1.0F, 1.0F, 0.0F},
                                {1.0F, -1.0F, 1.0F, 1.0F},
                                {-1.0F, 1.0F, 0.0F, 0.0F},
                                {1.0F, -1.0F, 1.0F, 1.0F},
                                {-1.0F, -1.0F, 0.0F, 1.0F}
                            };

                            for (int i = 0; i < 6; i++) {
                                float[] k = qx[i];
                                System.arraycopy(
                                    new float[]{
                                        (float)p[0],
                                        (float)p[1],
                                        (float)p[2],
                                        (float)(k[0] * s),
                                        (float)(k[1] * s),
                                        k[2],
                                        k[3],
                                        (float)c[0],
                                        (float)c[1],
                                        (float)c[2],
                                        1.0F
                                    },
                                    0,
                                    a,
                                    i * 11,
                                    11
                                );
                            }

                            GL33C.glBufferSubData(34962, nAll * 44L, a);
                            GL33C.glDrawArrays(4, nAll, 6);
                        }
                    }

                    GL33C.glBlendFunc(770, 771);
                    GL33C.glDisable(3042);
                    GL33C.glEnable(2929);
                };
            }

            BiConsumer<float[], Boolean> worldPass = (M, clip) -> {
                GL33C.glUseProgram(this.pw.id);
                GL33C.glUniformMatrix4fv(this.pw.u("uVP"), false, VP);
                GL33C.glUniform1f(this.pw.u("uFx"), FX ? 1.0F : 0.0F);
                GL33C.glUniformMatrix4fv(this.pw.u("uLVP"), false, LVPf);
                u3(this.pw, "uLd", Ld);
                u3(this.pw, "uSunC", sunC);
                u3(this.pw, "uAmb", amb);
                GL33C.glUniform3f(this.pw.u("uCamO"), (float)cam[0], (float)(clip ? -0.25 - cam[1] : cam[1]), (float)cam[2]);
                GL33C.glUniform1i(this.pw.u("uSM"), 3);
                GL33C.glUniformMatrix4fv(this.pw.u("uM"), false, M);
                GL33C.glUniform1f(this.pw.u("uT"), (float)t);
                GL33C.glUniform2fv(this.pw.u("uF"), this.fe);
                u3(this.pw, "uL", lt);
                u3(this.pw, "uFog", fogC);
                u3(this.pw, "uCam", cam);
                GL33C.glUniform2f(this.pw.u("uFr"), FX ? 70.0F : 40.0F, FX ? 340.0F : 250.0F);
                GL33C.glUniform1f(this.pw.u("uClip"), clip ? 1.0F : 0.0F);
                GL33C.glUniform1f(this.pw.u("uMode"), 0.0F);
                GL33C.glUniform1i(this.pw.u("uA"), 0);
                GL33C.glUniform1i(this.pw.u("uWt"), 1);
                GL33C.glUniform1i(this.pw.u("uR"), 2);
                GL33C.glActiveTexture(33984);
                GL33C.glBindTexture(3553, this.txa);
                GL33C.glBindVertexArray(this.vaoW);
                GL33C.glDrawArrays(4, 0, this.nW);
                GL33C.glBindVertexArray(this.vaoS);
                GL33C.glDrawArrays(4, 0, this.nSB);
                GL33C.glUniform1f(this.pw.u("uMode"), 2.0F);
                GL33C.glUniformMatrix4fv(this.pw.u("uM"), false, mul(M, TRc));
                GL33C.glBindVertexArray(this.vaoCl);
                GL33C.glDrawArrays(4, 0, this.nCl);
                GL33C.glUniform1f(this.pw.u("uMode"), 0.0F);
                GL33C.glUniformMatrix4fv(this.pw.u("uM"), false, M);
            };
            GL33C.glBindFramebuffer(36160, this.fb);
            GL33C.glViewport(0, 0, this.rw, this.rh);
            GL33C.glClear(256);
            skyPass[0].run();
            worldPass.accept(REFL, true);
            GL33C.glBindFramebuffer(36160, this.mfb);
            GL33C.glViewport(0, 0, this.tw, this.th);
            GL33C.glClear(256);
            skyPass[1].run();
            worldPass.accept(I4, false);
            GL33C.glEnable(3042);
            GL33C.glBlendFunc(770, 771);
            GL33C.glDepthMask(false);
            GL33C.glUniform1f(this.pw.u("uMode"), 1.0F);
            GL33C.glUniform1f(this.pw.u("uFrame"), fr);
            GL33C.glUniform2f(this.pw.u("uRes"), this.tw, this.th);
            GL33C.glActiveTexture(33985);
            GL33C.glBindTexture(3553, this.txw);
            GL33C.glActiveTexture(33986);
            GL33C.glBindTexture(3553, this.fbt);
            GL33C.glActiveTexture(33984);
            GL33C.glBindVertexArray(this.vaoWater);
            GL33C.glDrawArrays(4, 0, this.nWater);
            GL33C.glUseProgram(this.pp.id);
            GL33C.glBindTexture(3553, this.txa);
            GL33C.glBindVertexArray(this.vaoP);
            GL33C.glDrawArrays(4, 0, nN);
            if (nA > 0) {
                GL33C.glBlendFunc(770, 1);
                GL33C.glDrawArrays(4, nN, nA);
                GL33C.glBlendFunc(770, 771);
            }

            GL33C.glDepthMask(true);
            GL33C.glDisable(3042);
            GL33C.glBindFramebuffer(36160, this.outFbo);
            GL33C.glViewport(0, 0, this.tw, this.th);
            GL33C.glDisable(2929);
            GL33C.glActiveTexture(33984);
            GL33C.glBindTexture(3553, this.mtx);
            GL33C.glBindVertexArray(this.vaoEmpty);
            if (FX) {
                GL33C.glGenerateMipmap(3553);
                GL33C.glActiveTexture(33985);
                GL33C.glBindTexture(3553, this.mdt);
                GL33C.glUseProgram(this.ppo.id);
                GL33C.glUniform1i(this.ppo.u("uC"), 0);
                GL33C.glUniform1i(this.ppo.u("uD"), 1);
                double[] spos = add(cam, sunD, 300.0);
                double[] q = new double[4];

                for (int i = 0; i < 4; i++) {
                    q[i] = VP[i] * spos[0] + VP[4 + i] * spos[1] + VP[8 + i] * spos[2] + VP[12 + i];
                }

                double sx = q[0] / q[3] * 0.5 + 0.5;
                double sy = q[1] / q[3] * 0.5 + 0.5;
                double edge = clamp01(1.4 - Math.max(Math.abs(sx - 0.5), Math.abs(sy - 0.5)) * 2.0);
                GL33C.glUniform2f(this.ppo.u("uSun"), (float)sx, (float)sy);
                GL33C.glUniform1f(this.ppo.u("uSunV"), q[3] > 0.0 ? (float)(edge * sunI * (0.45 + 0.55 * glow)) : 0.0F);
                u3(this.ppo, "uRay", warm);
                GL33C.glUniform1f(this.ppo.u("uExp"), (float)(lerp(1.55, 2.1, glow) * lerp(1.0, 1.5, night)));
                GL33C.glDrawArrays(4, 0, 3);
                GL33C.glActiveTexture(33984);
            } else {
                GL33C.glUseProgram(this.pcopy.id);
                GL33C.glUniform1i(this.pcopy.u("uC"), 0);
                GL33C.glDrawArrays(4, 0, 3);
            }
        }
    }

    static {
        String[][] m = new String[][]{
            {"gtop", "block/grass_block_top"},
            {"dirt", "block/dirt"},
            {"sand", "block/sand"},
            {"stone", "block/stone"},
            {"snow", "block/snow"},
            {"gsnow", "block/grass_block_snow"},
            {"clog", "block/cherry_log"},
            {"clogt", "block/cherry_log_top"},
            {"cleaf", "block/cherry_leaves"},
            {"cane", "block/sugar_cane"},
            {"poppy", "block/poppy"},
            {"dand", "block/dandelion"},
            {"azure", "block/azure_bluet"},
            {"grassp", "block/short_grass"},
            {"sap", "block/cherry_sapling"},
            {"ppet", "block/pink_petals"},
            {"lily", "block/lily_pad"},
            {"oakl", "block/oak_log"},
            {"oakt", "block/oak_log_top"},
            {"oakf", "block/oak_leaves"},
            {"birl", "block/birch_log"},
            {"birt", "block/birch_log_top"},
            {"birf", "block/birch_leaves"},
            {"tulr", "block/red_tulip"},
            {"tulo", "block/orange_tulip"},
            {"tulp", "block/pink_tulip"},
            {"tulw", "block/white_tulip"},
            {"corn", "block/cornflower"},
            {"alli", "block/allium"},
            {"daisy", "block/oxeye_daisy"},
            {"lotv", "block/lily_of_the_valley"},
            {"tgb", "block/tall_grass_bottom"},
            {"tgt", "block/tall_grass_top"},
            {"fern", "block/fern"},
            {"lilb", "block/lilac_bottom"},
            {"lilt", "block/lilac_top"},
            {"roseb", "block/rose_bush_bottom"},
            {"roset", "block/rose_bush_top"},
            {"peob", "block/peony_bottom"},
            {"peot", "block/peony_top"},
            {"glint", "particle/glint"},
            {"bone", "item/bone_meal"},
            {"fly", "particle/firefly"}
        };

        for (String[] e : m) {
            PATH.put(e[0], e[1]);
        }

        for (int i = 0; i < 12; i++) {
            PATH.put("p" + i, "particle/cherry_" + i);
        }

        TINT = Map.of(
            "gtop",
            9551193,
            "cane",
            9551193,
            "grassp",
            9551193,
            "tgb",
            9551193,
            "tgt",
            9551193,
            "fern",
            9551193,
            "oakf",
            7842607,
            "birf",
            8431445,
            "lily",
            2129968
        );
        WL = new int[]{3, 2, 1, 3, 4, 1};
        I4 = new float[]{1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F};
        REFL = new float[]{1.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, -0.25F, 0.0F, 1.0F};
        SKY = new double[][]{
            {0.0, 110.0, 155.0, 255.0, 187.0, 211.0, 255.0, 255.0, 255.0, 255.0},
            {50.0, 58.0, 74.0, 133.0, 244.0, 162.0, 89.0, 255.0, 212.0, 176.0},
            {75.0, 20.0, 26.0, 58.0, 58.0, 46.0, 90.0, 118.0, 120.0, 178.0},
            {100.0, 7.0, 10.0, 24.0, 20.0, 32.0, 64.0, 72.0, 86.0, 146.0}
        };
        FLY = new double[26][];

        for (int i = 0; i < 26; i++) {
            double a = i / 26.0 * 6.283;
            FLY[i] = new double[]{
                1.0 + Math.cos(a) * (5.0 + World.hash(i, 1.0) * 6.0),
                0.6 + World.hash(i, 2.0) * 2.4,
                -4.0 + Math.sin(a) * (4.0 + World.hash(i, 3.0) * 5.0),
                World.hash(i, 4.0) * 6.28,
                0.4 + World.hash(i, 5.0) * 0.6
            };
        }
    }

    @Environment(EnvType.CLIENT)
    private record Ev(double at, Runnable fn) {
    }

    @Environment(EnvType.CLIENT)
    private record Look(float[] view, double[] fw, double[] rt, double[] up) {
    }

    @Environment(EnvType.CLIENT)
    private static final class P {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        double ph;
        double life;
        double max;
        double sz;
        double land = -1.0;
        String tile;
        boolean pet;
        boolean glow;
        boolean fly;
        double[] b;
    }

    @Environment(EnvType.CLIENT)
    private static final class Prog {
        final int id;
        final Map<String, Integer> loc = new HashMap<>();

        Prog(String vs, String fs) {
            this.id = GL33C.glCreateProgram();
            int v = sh(35633, vs);
            int f = sh(35632, fs);
            GL33C.glAttachShader(this.id, v);
            GL33C.glAttachShader(this.id, f);
            GL33C.glLinkProgram(this.id);
            if (GL33C.glGetProgrami(this.id, 35714) == 0) {
                throw new IllegalStateException("link: " + GL33C.glGetProgramInfoLog(this.id));
            }

            GL33C.glDeleteShader(v);
            GL33C.glDeleteShader(f);
        }

        int u(String name) {
            return this.loc.computeIfAbsent(name, n -> GL33C.glGetUniformLocation(this.id, n));
        }

        private static int sh(int type, String src) {
            int s = GL33C.glCreateShader(type);
            GL33C.glShaderSource(s, src);
            GL33C.glCompileShader(s);
            if (GL33C.glGetShaderi(s, 35713) == 0) {
                throw new IllegalStateException("compile: " + GL33C.glGetShaderInfoLog(s));
            } else {
                return s;
            }
        }
    }
}
