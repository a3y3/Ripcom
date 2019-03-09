import java.util.Timer;
import java.util.TimerTask;

public class Rover extends TimerTask {
    private int id;
    final int UPDATE_FREQUENCY = 5000;

    public int getId() {
        return id;
    }

    public static void main(String[] args) {
        Timer timer = new Timer();
        timer.schedule(new Rover(), 0, 5000);
    }

    @Override
    public void run() {
        System.out.println("Hi!");
    }
}
