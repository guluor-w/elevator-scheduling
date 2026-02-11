import java.util.ArrayList;

public class PassengerQueue {
    private final ArrayList<Passenger> passengers = new ArrayList<>();
    private volatile boolean isEnd = false;
    private volatile boolean noMoreNew = false;

    public synchronized void offer(Passenger passenger) {
        if (passenger != null) {
            passengers.add(passenger);
        }
        this.notifyAll();
    }

    public synchronized Passenger poll() {
        if (passengers.isEmpty() && !isEnd) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (passengers.isEmpty()) {
            return null;
        }
        return passengers.remove(0);
    }

    public synchronized void setEnd() {
        isEnd = true;
    }

    public synchronized boolean isEnd() {
        return isEnd;
    }

    public synchronized boolean isEmpty() {
        return passengers.isEmpty();
    }

    public synchronized void setNoMoreNew() {
        noMoreNew = true;
        this.notifyAll();
    }

    public synchronized boolean isNoMoreNew() {
        return noMoreNew;
    }

}
