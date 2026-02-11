import com.oocourse.elevator3.TimableOutput;
import java.util.concurrent.ConcurrentHashMap;

public class DispatchThread extends Thread {
    private final PassengerQueue passengerQueue;
    private final ConcurrentHashMap<Integer, ElevatorRequestQueue> queueMap;
    private int count;

    public DispatchThread(PassengerQueue passengerQueue,
        ConcurrentHashMap<Integer, ElevatorRequestQueue> queueMap) {
        this.passengerQueue = passengerQueue;
        this.queueMap = queueMap;
        this.count = 0;
    }

    @Override
    public void run() {
        while (true) {
            Passenger passenger;
            if (passengerQueue.isNoMoreNew() && !passengerQueue.isEnd()) {   //End标识着结束输入
                boolean end = true;
                for (ElevatorRequestQueue queue : queueMap.values()) {
                    if (!queue.noMoreReturn()) {
                        end = false;
                        break;
                    }
                }
                if (end) {
                    passengerQueue.setEnd();
                }
            }

            if (passengerQueue.isEmpty() && passengerQueue.isEnd()) {
                for (ElevatorRequestQueue queue : queueMap.values()) {
                    queue.setEnd();
                }
                //System.out.println("调度线程结束");
                break;
            }
            passenger = passengerQueue.poll();

            if (passenger == null) {
                continue;
            }
            try {
                dispatch(passenger);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void dispatch(Passenger passenger) throws InterruptedException {
        int num = 0;
        while (true) {
            synchronized (queueMap.get(count % 6 + 1)) {
                if (queueMap.get(count % 6 + 1).isSilent()) {
                    count++;
                    num++;
                }
                else {
                    TimableOutput.println("RECEIVE-" + passenger.getPersonId()
                        + "-" + (count % 6 + 1));
                    queueMap.get(count % 6 + 1).offer(passenger);
                    count++;
                    break;
                }
            }
            if (num == 6) {
                sleep(6000);
                num = 0;
            }
        }
    }

}