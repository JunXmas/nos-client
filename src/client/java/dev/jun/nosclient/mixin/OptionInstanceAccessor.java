package dev.jun.nosclient.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gamma vượt 100% (full bright): set() của game chặn giá trị ngoài 0–1, nên ghi thẳng vào field. */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
	@Accessor("value")
	void nosclient$setRaw(Object v);
}
