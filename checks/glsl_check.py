"""Biên dịch mọi shader core/*.fsh|*.vsh của Nos client bằng Mesa (EGL surfaceless, llvmpipe) để bắt lỗi cú pháp
trước khi vào game — game chỉ báo lỗi trong log rồi vẽ màn đen.

Chạy: python3 -I checks/glsl_check.py <thư mục shaders/core> <thư mục include của minecraft>
Lấy include: unzip minecraft-client.jar 'assets/minecraft/shaders/include/*'.
Mỗi #ifdef trong file được thử bật riêng (OUTLINE, SHADOW, …) vì pipeline biên dịch từng biến thể.
Chạy hai lượt: Mesa (khắt khe, bắt được từ khoá dành riêng như `half`) và NVIDIA (máy chơi thật). Mỗi lượt là
một tiến trình con vì glvnd chọn driver EGL lúc nạp thư viện.
"""
import ctypes, os, re, sys
from pathlib import Path

core, inc = Path(sys.argv[1]), Path(sys.argv[2])
VENDORS = Path("/usr/share/glvnd/egl_vendor.d")
if "NOS_GLSL_VENDOR" not in os.environ:
    import subprocess
    worst = 0
    for v in sorted(VENDORS.glob("*.json")) or [None]:
        env = dict(os.environ, NOS_GLSL_VENDOR=v.stem if v else "default")
        if v:
            env["__EGL_VENDOR_LIBRARY_FILENAMES"] = str(v)
        print(f"--- {env['NOS_GLSL_VENDOR']}", flush=True)
        worst = max(worst, subprocess.run([sys.executable, "-I", *sys.argv], env=env).returncode)
    sys.exit(worst)
os.environ.setdefault("EGL_PLATFORM", "surfaceless")
os.environ.setdefault("LIBGL_ALWAYS_SOFTWARE", "1")
egl = ctypes.CDLL("libEGL.so.1")
gl = ctypes.CDLL("libGL.so.1")
egl.eglGetDisplay.restype = ctypes.c_void_p
egl.eglCreateContext.restype = ctypes.c_void_p
egl.eglGetProcAddress.restype = ctypes.c_void_p
egl.eglGetProcAddress.argtypes = [ctypes.c_char_p]
dpy = ctypes.c_void_p(egl.eglGetDisplay(ctypes.c_void_p(0)))
assert egl.eglInitialize(dpy, None, None), "eglInitialize thất bại"
egl.eglBindAPI(0x30A2)  # EGL_OPENGL_API
cfg = ctypes.c_void_p(); n = ctypes.c_int()
egl.eglChooseConfig(dpy, (ctypes.c_int * 3)(0x3040, 0x0008, 0x3038), ctypes.byref(cfg), 1, ctypes.byref(n))  # RENDERABLE_TYPE=OPENGL_BIT
ctx = egl.eglCreateContext(dpy, cfg, None, (ctypes.c_int * 7)(0x3098, 3, 0x30FB, 3, 0x30FD, 1, 0x3038))  # 3.3 core
assert ctx, "không tạo được context GL 3.3"
assert egl.eglMakeCurrent(dpy, None, None, ctypes.c_void_p(ctx))

def fn(name, res, *args):
    f = ctypes.CFUNCTYPE(res, *args)(egl.eglGetProcAddress(name.encode()))
    return f
glCreateShader = fn("glCreateShader", ctypes.c_uint, ctypes.c_uint)
glShaderSource = fn("glShaderSource", None, ctypes.c_uint, ctypes.c_int, ctypes.POINTER(ctypes.c_char_p), ctypes.c_void_p)
glCompileShader = fn("glCompileShader", None, ctypes.c_uint)
glGetShaderiv = fn("glGetShaderiv", None, ctypes.c_uint, ctypes.c_uint, ctypes.POINTER(ctypes.c_int))
glGetShaderInfoLog = fn("glGetShaderInfoLog", None, ctypes.c_uint, ctypes.c_int, ctypes.c_void_p, ctypes.c_char_p)

def expand(src, seen=()):
    def sub(m):
        ns, path = m.group(1), m.group(2)
        f = inc / path
        return re.sub(r"(?m)^#version.*$", "", expand(f.read_text(), seen)) if f.exists() else m.group(0)
    return re.sub(r"#moj_import <(\w+):([\w/.]+)>", sub, src)

def compile_one(src, kind):
    sh = glCreateShader(0x8B30 if kind == "fsh" else 0x8B31)
    arr = (ctypes.c_char_p * 1)(src.encode())
    glShaderSource(sh, 1, arr, None); glCompileShader(sh)
    ok = ctypes.c_int(); glGetShaderiv(sh, 0x8B81, ctypes.byref(ok))
    log = ctypes.create_string_buffer(4096); glGetShaderInfoLog(sh, 4096, None, log)
    return ok.value, log.value.decode(errors="replace").strip()

bad = 0
for f in sorted(core.glob("*.[fv]sh")):
    src = expand(f.read_text())
    defines = sorted({a or b for a, b in re.findall(r"#ifdef (\w+)|defined\((\w+)\)", src)}) or [None]
    for d in [None] + [x for x in defines if x]:
        s = src if d is None else src.replace("\n", f"\n#define {d}\n", 1)
        ok, log = compile_one(s, f.suffix[1:])
        tag = f"{f.name}" + (f" [{d}]" if d else "")
        if ok:
            print("OK  ", tag)
        else:
            bad += 1; print("FAIL", tag, "\n    " + log.replace("\n", "\n    "))
sys.exit(1 if bad else 0)
