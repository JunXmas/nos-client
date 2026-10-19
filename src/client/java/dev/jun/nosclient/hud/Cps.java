package dev.jun.nosclient.hud;

/**
 * CPS theo spec: giữ dấu thời gian các cú bấm, bỏ cú cũ hơn 1 giây. Vòng đệm cấp sẵn — không cấp phát mỗi cú bấm.
 * Hơn 64 cú/giây thì cú cũ nhất bị đè (người không bấm nổi, chỉ macro mới tới).
 */
public final class Cps {
	private final long[] t = new long[64];
	private int head, size;

	public synchronized void click(long nowNanos) {
		t[head] = nowNanos;
		head = (head + 1) % t.length;
		if (size < t.length) size++;
	}

	public synchronized int count(long nowNanos) {
		while (size > 0 && nowNanos - t[(head - size + t.length) % t.length] > 1_000_000_000L) size--;
		return size;
	}

	/** Tự kiểm: java -cp build/classes/java/client dev.jun.nosclient.hud.Cps */
	public static void main(String[] a) {
		Cps c = new Cps();
		long s = 1_000_000_000L;
		for (int i = 0; i < 10; i++) c.click(i * s / 10); // 10 cú trong giây đầu
		check(c.count(s - 1) == 10, "10 cú trong 1 giây");
		check(c.count(s + s / 20) == 9, "cú đầu (t=0) quá 1 giây thì rơi");
		check(c.count(3 * s) == 0, "sau 2 giây không bấm là 0");
		for (int i = 0; i < 100; i++) c.click(5 * s + i);
		check(c.count(5 * s + 200) == 64, "đệm đầy thì chặn ở 64, không tràn");
		System.out.println("CPS OK");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
