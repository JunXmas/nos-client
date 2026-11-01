package dev.jun.nosclient.lyricsflow.mixin;

import dev.jun.nosclient.lyricsflow.render.CustomFonts;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
   @Shadow
   @Final
   @Mutable
   private Set<RepositorySource> sources;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void lyricsflow$addFonts(RepositorySource[] given, CallbackInfo ci) {
      boolean client = false;

      for (RepositorySource s : this.sources) {
         if (s instanceof ClientPackSource) {
            client = true;
         }
      }

      if (client) {
         Set<RepositorySource> set = new LinkedHashSet<>(this.sources);
         set.add(CustomFonts.source());
         this.sources = set;
      }
   }
}
