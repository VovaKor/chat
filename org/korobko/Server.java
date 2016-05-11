package org.korobko;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Вова on 09.03.2016.
 */
public class Server
{
    private static Map<String, Connection> connectionMap = new ConcurrentHashMap<>();
    public static void sendBroadcastMessage(Message message){

        for (Map.Entry<String, Connection> pair :
             connectionMap.entrySet())
        {
            try
            {
                pair.getValue().send(message);
            }
            catch (IOException e)
            {
                ConsoleHelper.writeMessage("Не удалось отправить сообщение");
            }
        }
    }
    public static void main(String[] args)
    {
        ConsoleHelper.writeMessage("Введите порт сервера");
        int serverPort = ConsoleHelper.readInt();

        try (ServerSocket serverSocket = new ServerSocket(serverPort))
        {
            ConsoleHelper.writeMessage("Сервер запущен");
            while (true)
            {

                    Socket socket = serverSocket.accept();
                    Handler handler = new Handler(socket);
                    handler.start();

            }
        }
        catch (IOException e)
        {
            ConsoleHelper.writeMessage("Не удалось запустить сервер");
        }

    }
    private static class Handler extends Thread{
        private Socket socket;

        public Handler(Socket socket)
        {
            this.socket = socket;
        }
        public void run(){

            SocketAddress socketAddress=null;
            String userName = null;
            try (Connection connection = new Connection(socket))
            {
                socketAddress = connection.getRemoteSocketAddress();
                ConsoleHelper.writeMessage("Установлено новое соединение с удаленным адресом: "
                        + socketAddress);
                userName = serverHandshake(connection);
                sendBroadcastMessage(new Message(MessageType.USER_ADDED, userName));
                sendListOfUsers(connection, userName);
                serverMainLoop(connection, userName);
            }catch (Exception e){
                ConsoleHelper.writeMessage("Произошла ошибка обмена данных с удаленным адресом: "
                        + socketAddress);
            } finally {
                if (userName != null) {
                    connectionMap.remove(userName);
                    sendBroadcastMessage(new Message(MessageType.USER_REMOVED, userName));

                }
            }
            ConsoleHelper.writeMessage("Закрыто соединение с удаленным адресом: " + socketAddress);
        }
        private void serverMainLoop(Connection connection, String userName) throws
                IOException, ClassNotFoundException{
            while (true)
            {
                Message message = connection.receive();
                if (message.getType()== MessageType.TEXT){
                    String data = userName+": "+message.getData();
                    Message m = new Message(MessageType.TEXT,data);
                    sendBroadcastMessage(m);
                }else ConsoleHelper.writeMessage("Неправильный тип сообщения");

            }
        }
        private void sendListOfUsers(Connection connection, String userName) throws IOException{
            for (Map.Entry<String, Connection> pair :
                    connectionMap.entrySet())
            {
                if (!pair.getKey().equals(userName)){
                    String name = pair.getKey();
                    connection.send(new Message(MessageType.USER_ADDED,name));
                }


            }
        }

        private String serverHandshake(Connection connection) throws IOException,
                ClassNotFoundException{
            while (true) {
                connection.send(new Message(MessageType.NAME_REQUEST));
                Message message = connection.receive();
                if (message.getType() == MessageType.USER_NAME) {
                    String userName = message.getData();
                    if (userName != null && !userName.isEmpty() && !connectionMap.containsKey(userName)) {
                        connectionMap.put(userName, connection);
                        connection.send(new Message(MessageType.NAME_ACCEPTED));
                        return userName;
                    }
                }
            }
        }
    }
}
