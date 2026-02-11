import com.oocourse.elevator3.TimableOutput;
import java.util.concurrent.ConcurrentHashMap;

public class MainClass {
    public static void main(String[] args) {
        TimableOutput.initStartTimestamp();

        PassengerQueue passengerQueue = new PassengerQueue();
        int[] elevatorIds = {1,2,3,4,5,6};
        ConcurrentHashMap<Integer, ElevatorRequestQueue> queueMap = new ConcurrentHashMap<>();

        for (int elevatorId : elevatorIds) {
            ElevatorRequestQueue queue = new ElevatorRequestQueue();
            queueMap.put(elevatorId, queue);
            ElevatorThread elevatorThread = new ElevatorThread(elevatorId, queue, passengerQueue);
            elevatorThread.start();
        }
        DispatchThread dispatcher = new DispatchThread(passengerQueue, queueMap);
        InputThread inputThread = new InputThread(passengerQueue, queueMap);
        inputThread.start();
        dispatcher.start();
    }
}
