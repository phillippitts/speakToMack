import javax.sound.sampled.*;

public class ListDevices {
    public static void main(String[] args) throws Exception {
        AudioFormat fmt = new AudioFormat(16000, 16, 1, true, false);
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, fmt);
        String[] devices = {"MacBook Pro Microphone", "Logitech StreamCam"};

        for (String deviceName : devices) {
            System.out.println("=== Testing: " + deviceName + " ===");
            try {
                Mixer.Info[] mixers = AudioSystem.getMixerInfo();
                TargetDataLine line = null;
                for (Mixer.Info info : mixers) {
                    if (info.getName().equalsIgnoreCase(deviceName)) {
                        Mixer m = AudioSystem.getMixer(info);
                        line = (TargetDataLine) m.getLine(targetInfo);
                        break;
                    }
                }
                if (line == null) {
                    System.out.println("  Device not found");
                    continue;
                }
                line.open(fmt);
                line.start();
                // Read 500ms of audio
                byte[] buf = new byte[16000];
                int n = line.read(buf, 0, buf.length);
                line.stop();
                line.close();

                boolean allZero = true;
                long sum = 0;
                for (int i = 0; i < n; i++) {
                    if (buf[i] != 0) allZero = false;
                    sum += Math.abs(buf[i]);
                }
                double avgLevel = (double) sum / n;

                System.out.println("  Read " + n + " bytes");
                System.out.println("  All zeros: " + allZero);
                System.out.println("  Avg level: " + String.format("%.2f", avgLevel));
                System.out.println(allZero ? "  >>> SILENT" : "  >>> ACTIVE");
            } catch (Exception e) {
                System.out.println("  ERROR: " + e.getMessage());
            }
        }
    }
}