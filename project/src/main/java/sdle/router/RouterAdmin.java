package sdle.router;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sdle.router.utils.Message;

public class RouterAdmin {
    public static void main(String[] args) {
        //send a message to the router in port 6000
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect("tcp://localhost:6000");

            Message message = new Message();
            message.setMethod("leaveHashRing");
            message.setServerId("5");

            socket.send(message.toJson().getBytes(ZMQ.CHARSET));
        }
    }
}
