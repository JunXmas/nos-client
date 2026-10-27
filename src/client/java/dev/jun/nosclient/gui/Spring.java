package dev.jun.nosclient.gui;

/**
 * Lò xo một chiều cho chuyển động UI: đổi đích giữa chừng vẫn giữ vận tốc nên không giật như tween
 * chạy lại từ đầu. zeta < 1 thì vượt đích nhẹ rồi lắng, zeta = 1 thì về êm không vượt.
 */
public final class Spring {
    public float value;
    public float velocity;

    public Spring(float value) {
        this.value = value;
    }

    public float step(float target, float dt, float omega, float zeta) {
        // Bước nhỏ cố định: ổn định cả khi tụt khung (dt bị chặn 0.05 s ở nơi gọi).
        for (float left = Math.min(dt, 0.1F); left > 0.0F; left -= 0.004F) {
            float h = Math.min(0.004F, left);
            this.velocity += (omega * omega * (target - this.value) - 2.0F * zeta * omega * this.velocity) * h;
            this.value += this.velocity * h;
        }

        return this.value;
    }

    public void snap(float value) {
        this.value = value;
        this.velocity = 0.0F;
    }

    public boolean resting(float target) {
        return Math.abs(target - this.value) < 0.001F && Math.abs(this.velocity) < 0.01F;
    }
}
