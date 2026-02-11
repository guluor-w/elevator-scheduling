
import com.oocourse.elevator3.ElevatorInput;
import com.oocourse.elevator3.PersonRequest;
import com.oocourse.elevator3.ScheRequest;
import com.oocourse.elevator3.Request;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class InputThread extends Thread {
    private final PassengerQueue passengerQueue;
    private final ConcurrentHashMap<Integer, ElevatorRequestQueue> queueMap;

    public InputThread(PassengerQueue passengerQueue, ConcurrentHashMap<Integer,
        ElevatorRequestQueue> queueMap) {
        this.passengerQueue = passengerQueue;
        this.queueMap = queueMap;
    }

    @Override
    public void run() {
        ElevatorInput elevatorInput = new ElevatorInput(System.in);
        while (true) {
            Request request = elevatorInput.nextRequest();
            if (request == null) {
                passengerQueue.setNoMoreNew();
                //System.out.println("输入线程结束");
                break;
            } else {
                if (request instanceof PersonRequest) {
                    PersonRequest personRequest = (PersonRequest) request;
                    Passenger newPassenger = new Passenger(personRequest);
                    passengerQueue.offer(newPassenger);
                } else if (request instanceof ScheRequest) {
                    ScheRequest scheRequest = (ScheRequest) request;
                    int elevatorId = scheRequest.getElevatorId();
                    ElevatorRequestQueue queue = queueMap.get(elevatorId);
                    queue.haveTempTask(scheRequest);
                }
            }
        }
        try {
            elevatorInput.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
