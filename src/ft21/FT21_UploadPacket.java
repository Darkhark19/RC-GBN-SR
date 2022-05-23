package ft21;

public class FT21_UploadPacket extends FT21Packet {
	public final String filename;
	
	public FT21_UploadPacket(String filename) {
		super(PacketType.UPLOAD);
		super.putByte(Integer.BYTES);
		super.putInt(0);
		this.filename = filename;
		super.putString(filename);
	}
	
	
	
	public String toString() {
		return String.format("UPLOAD<%s>", filename);
	}
}
