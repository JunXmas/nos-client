package dev.jun.nosclient.cosmetic;

/** A local try-on, deliberately separate from account ownership or premium grants. */
public record CosmeticLook(String theme, boolean wings, boolean cape, boolean headband) {
	public static final CosmeticLook NONE = new CosmeticLook("angel", false, false, false);
	public CosmeticLook {
		if (!theme.equals("angel") && !theme.equals("crimson")) throw new IllegalArgumentException("Unknown cosmetic theme");
	}
	public boolean any() { return wings || cape || headband; }
	public boolean visible(String slot) {
		return switch (slot) { case "wings" -> wings; case "cape" -> cape; case "headband" -> headband; default -> false; };
	}
}
