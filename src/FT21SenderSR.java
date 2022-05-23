import cnss.simulator.Node;
import ft21.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.SortedMap;
import java.util.TreeMap;



//import java.util.Map.Entry;

public class FT21SenderSR extends FT21AbstractSenderApplication {
	private static final int TIMEOUT = 1000;

	static int RECEIVER = 1;

	enum State {
		BEGINNING, UPLOADING, FINISHING, FINISHED
	};

	private SortedMap<Integer, byte[]> window = new TreeMap<>();
	private int[] times;
	private boolean[] acked;

	static int DEFAULT_TIMEOUT = 1000;

	private File file;
	private RandomAccessFile raf;
	private int BlockSize;
	private int nextPacketSeqN, lastPacketSeqN; // seqn do proximo ,seqn do ultimo

	private State state;
	private int lastPacketSent; // tempo do ultimo enviado
	private int windowSize;
	private boolean send;
	private boolean stop;
	

	public FT21SenderSR() {
		super(true, "FT21SenderSR");
	}

	public int initialise(int now, int node_id, Node nodeObj, String[] args) {
		super.initialise(now, node_id, nodeObj, args);

		raf = null;
		file = new File(args[0]);
		BlockSize = Integer.parseInt(args[1]);
		this.windowSize = Integer.parseInt(args[2]);
		state = State.BEGINNING;
		lastPacketSeqN = (int) Math.ceil(file.length() / (double) BlockSize);
		nextPacketSeqN = 0;
		lastPacketSent = -1;
		send = false;
		stop = false;
		times = new int[lastPacketSeqN + 2];
		acked = new boolean[lastPacketSeqN + 2];
		return 1;
	}

	// volta ao inicio da janela ou ao ultimo pacote
	public void on_time_out(int now) {
		nextPacketSeqN = window.firstKey();
		stop = false;
		if (nextPacketSeqN + 1 > lastPacketSeqN) {
			state = State.FINISHING;
			send = false;
		}
		sendNextPacket(now);
		
		

	}

	public void on_clock_tick(int now) {
		boolean hasSpace = window.size() < windowSize;
		if (hasSpace)
			if (state == State.BEGINNING  && !send) {
				sendNextPacket(now);
			} else if (state == State.UPLOADING )
				if( !stop && (nextPacketSeqN <= lastPacketSeqN)) {
					sendNextPacket(now);
			} else if (state == State.FINISHING && !send) {
				sendNextPacket(now);
			}
		if (state != State.FINISHED) {
			if (!window.isEmpty()) {
				Integer firstSeqN = window.firstKey();
				int time = times[firstSeqN];
				boolean canSend = now - time >= TIMEOUT;
				if (canSend) {
					super.on_clock_tick(now);
					on_time_out(now);
				}

			}
		}
	}

	private void sendNextPacket(int now) {

		switch (state) {
		case BEGINNING:
			super.sendPacket(now, RECEIVER, new FT21_UploadPacket(file.getName()));
			send = true;
			window.putIfAbsent(nextPacketSeqN, null);
			break;
		case UPLOADING:
			super.sendPacket(now, RECEIVER, readDataPacket(file, nextPacketSeqN));
			window.putIfAbsent(nextPacketSeqN, readDataPacket(file, nextPacketSeqN).data);
			break;
		case FINISHING:
			super.sendPacket(now, RECEIVER, new FT21_FinPacket(nextPacketSeqN));
			send = true;
			break;
		case FINISHED:
		}
		lastPacketSent = now;
		times[nextPacketSeqN] = now;
		if(nextPacketSeqN <= lastPacketSeqN)
			nextPacketSeqN++;
	}

	@Override
	public void on_receive_ack(int now, int client, FT21_AckPacket ack) {
		switch (state) {
		case BEGINNING:
			state = State.UPLOADING;
		case UPLOADING:
			if (!window.isEmpty())
				for (int i = window.firstKey(); i <= Math.abs(ack.cSeqN); i++) {
					window.remove(i);
				}
			if (Math.abs(ack.cSeqN) + 1 > lastPacketSeqN) {
				state = State.FINISHING;
				window.putIfAbsent(Math.abs(ack.cSeqN) +1, null);
			} else if (ack.cSeqN < 0) {
				window.putIfAbsent(Math.abs(ack.cSeqN) + 1, null);
			} else if (ack.cSeqN < ack.lastSentSeqN) {
				stop = true;
				window.putIfAbsent(Math.abs(ack.cSeqN) + 1, null);

			}
			break;
		case FINISHING:
			super.log(now, "All Done. Transfer complete...");
			super.printReport(now);
			state = State.FINISHED;
			return;
		case FINISHED:

		}
	}

	private FT21_DataPacket readDataPacket(File file, int seqN) {
		try {
			if (raf == null)
				raf = new RandomAccessFile(file, "r");
			raf.seek(BlockSize * (seqN - 1));
			byte[] data = new byte[BlockSize];
			int nbytes = raf.read(data);
			return new FT21_DataPacket(seqN, data, nbytes);
		} catch (Exception x) {
			throw new Error("Fatal Error: " + x.getMessage());
		}
	}
}
