package edu.isi.nlp.strings.offsets;

public final class EDTOffset extends AbstractOffset<EDTOffset> {

  private EDTOffset(int val) {
    super(val);
  }

  public static EDTOffset asEDTOffset(int val) {
    return new EDTOffset(val);
  }

  @Override
  public EDTOffset shiftedCopy(final int shiftAmount) {
    return asEDTOffset(asInt() + shiftAmount);
  }

  @Override
  public String toString() {
    return "e" + asInt();
  }
}
