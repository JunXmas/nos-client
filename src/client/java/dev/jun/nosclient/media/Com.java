package dev.jun.nosclient.media;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import java.io.ByteArrayOutputStream;

final class Com {
    private static NativeLibrary combase;
    private static NativeLibrary shcore;
    static final Memory IID_IAsyncInfo = guid("00000036-0000-0000-c000-000000000046");
    static final Memory IID_IStream = guid("0000000c-0000-0000-c000-000000000046");

    private Com() {}

    static synchronized void init() {
        if (combase == null) {
            combase = NativeLibrary.getInstance("combase");
            shcore = NativeLibrary.getInstance("shcore");
        }

        combase.getFunction("RoInitialize").invokeInt(new Object[] {1});
    }

    static Memory guid(String s) {
        String h = s.replace("-", "");
        Memory m = new Memory(16L);
        long d1 = Long.parseLong(h.substring(0, 8), 16);
        int d2 = Integer.parseInt(h.substring(8, 12), 16);
        int d3 = Integer.parseInt(h.substring(12, 16), 16);
        m.setInt(0L, (int) d1);
        m.setShort(4L, (short) d2);
        m.setShort(6L, (short) d3);

        for (int i = 0; i < 8; i++) {
            m.setByte(8 + i, (byte) Integer.parseInt(h.substring(16 + i * 2, 18 + i * 2), 16));
        }

        return m;
    }

    static int call(Pointer obj, int index, Object... args) {
        Pointer vtbl = obj.getPointer(0L);
        Pointer fp = vtbl.getPointer((long) index * Native.POINTER_SIZE);
        Function f = Function.getFunction(fp, 63);
        Object[] all = new Object[args.length + 1];
        all[0] = obj;
        System.arraycopy(args, 0, all, 1, args.length);
        return f.invokeInt(all);
    }

    static void release(Pointer p) {
        if (p != null) {
            try {
                call(p, 2);
            } catch (Throwable var2) {
            }
        }
    }

    static Pointer outPtr(Pointer obj, int index, Object... args) {
        PointerByReference r = new PointerByReference();
        Object[] a = new Object[args.length + 1];
        System.arraycopy(args, 0, a, 0, args.length);
        a[args.length] = r;
        int hr = call(obj, index, a);
        return hr < 0 ? null : r.getValue();
    }

    static int outInt(Pointer obj, int index) {
        IntByReference r = new IntByReference();
        int hr = call(obj, index, r);
        return hr < 0 ? Integer.MIN_VALUE : r.getValue();
    }

    static long outLong(Pointer obj, int index) {
        LongByReference r = new LongByReference();
        int hr = call(obj, index, r);
        return hr < 0 ? Long.MIN_VALUE : r.getValue();
    }

    static Pointer queryInterface(Pointer obj, Memory iid) {
        PointerByReference r = new PointerByReference();
        int hr = call(obj, 0, iid, r);
        return hr < 0 ? null : r.getValue();
    }

    static Pointer createHString(String s) {
        PointerByReference r = new PointerByReference();
        char[] chars = s.toCharArray();
        Memory m = new Memory((chars.length + 1) * 2L);
        m.clear();

        for (int i = 0; i < chars.length; i++) {
            m.setChar(i * 2L, chars[i]);
        }

        int hr =
                combase.getFunction("WindowsCreateString")
                        .invokeInt(new Object[] {m, chars.length, r});
        return hr < 0 ? null : r.getValue();
    }

    static void deleteHString(Pointer h) {
        if (h != null) {
            combase.getFunction("WindowsDeleteString").invokeInt(new Object[] {h});
        }
    }

    static String takeHString(Pointer h) {
        if (h == null) {
            return "";
        }

        try {
            IntByReference len = new IntByReference();
            Pointer buf =
                    combase.getFunction("WindowsGetStringRawBuffer")
                            .invokePointer(new Object[] {h, len});
            return buf != null && len.getValue() > 0
                    ? new String(buf.getCharArray(0L, len.getValue()))
                    : "";
        } finally {
            deleteHString(h);
        }
    }

    static String hstringProp(Pointer obj, int index) {
        Pointer h = outPtr(obj, index);
        return takeHString(h);
    }

    static Pointer activationFactory(String runtimeClass, Memory iid) {
        Pointer name = createHString(runtimeClass);

        try {
            PointerByReference r = new PointerByReference();
            int hr =
                    combase.getFunction("RoGetActivationFactory")
                            .invokeInt(new Object[] {name, iid, r});
            return hr < 0 ? null : r.getValue();
        } finally {
            deleteHString(name);
        }
    }

    static Pointer await(Pointer op, long timeoutMs) {
        if (op == null) {
            return null;
        }

        Pointer info = queryInterface(op, IID_IAsyncInfo);

        try {
            long end = System.currentTimeMillis() + timeoutMs;
            int status = 0;

            while (info != null) {
                status = outInt(info, 7);
                if (status != 0) {
                    break;
                }

                if (System.currentTimeMillis() > end) {
                    return null;
                }

                try {
                    Thread.sleep(2L);
                } catch (InterruptedException e) {
                    return null;
                }
            }

            return status != 1 ? null : outPtr(op, 8);
        } finally {
            release(info);
            release(op);
        }
    }

    static void awaitAction(Pointer op) {
        if (op != null) {
            Pointer info = queryInterface(op, IID_IAsyncInfo);

            try {
                long end = System.currentTimeMillis() + 1500L;

                while (info != null && outInt(info, 7) == 0 && System.currentTimeMillis() < end) {
                    try {
                        Thread.sleep(2L);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } finally {
                release(info);
                release(op);
            }
        }
    }

    static byte[] readRandomAccessStream(Pointer ras, int maxBytes) {
        PointerByReference r = new PointerByReference();
        int hr =
                shcore.getFunction("CreateStreamOverRandomAccessStream")
                        .invokeInt(new Object[] {ras, IID_IStream, r});
        if (hr >= 0 && r.getValue() != null) {
            Pointer stream = r.getValue();

            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                Memory buf = new Memory(65536L);
                IntByReference read = new IntByReference();

                while (out.size() < maxBytes) {
                    read.setValue(0);
                    int h = call(stream, 3, buf, 65536, read);
                    int n = read.getValue();
                    if (n > 0) {
                        out.write(buf.getByteArray(0L, n), 0, n);
                    }

                    if (h != 0 || n <= 0) {
                        break;
                    }
                }

                return out.toByteArray();
            } finally {
                release(stream);
            }
        } else {
            return null;
        }
    }
}
