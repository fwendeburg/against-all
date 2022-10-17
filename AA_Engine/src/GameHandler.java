import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;

import org.bson.json.JsonMode;
import org.json.simple.JSONObject;

import Game.Ciudad;

import Utils.MessageParser;
import Utils.MessageParserException;

public class GameHandler extends Thread {
    private final AuthenticationHandler authThread;
    private final String ipBroker;
    private final int puertoBroker;
    private final String ipServidorClima;
    private final int puertoServidorClima;
    private ArrayList<Ciudad> ciudades;

    public GameHandler(AuthenticationHandler authThread, HashMap<String, Integer> jugadores, String ipBroker, int puertoBroker, String ipServidorCLima, int puertoServidorClima) {
        this.authThread = authThread;
        this.ipBroker = ipBroker;
        this.puertoBroker = puertoBroker;
        this.ipServidorClima = ipServidorCLima;
        this.puertoServidorClima = puertoServidorClima;
    
        this.ciudades = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            ciudades.add(new Ciudad());
        }
    }

    private String leeSocket(Socket socketCliente) throws IOException {
        DataInputStream dis = new DataInputStream(socketCliente.getInputStream());
        return dis.readUTF();
    }

    private void escribeSocket(String mensaje, Socket socketCliente) throws IOException {
        DataOutputStream dos = new DataOutputStream(socketCliente.getOutputStream());
        dos.writeUTF(mensaje);
    }

    private boolean mandarPeticion(JSONObject res, Socket socketCliente) throws IOException {
        MessageParser parser = new MessageParser();

        StringBuilder sb = new StringBuilder();
        sb.append(MessageParser.STXChar);
        sb.append(res.toString());
        sb.append(MessageParser.ETXChar);
        sb.append(parser.getStringLRC(res.toString()));

        escribeSocket(sb.toString(), socketCliente);
        return true;
    }

    public void guardarCiudades(JSONObject respuesta) {
        ArrayList<String> keysArray = new ArrayList<String>(respuesta.keySet());

        for (int i = 0; i < keysArray.size(); i++) {
            Ciudad c = ciudades.get(i);
            c.setNombre(keysArray.get(i));
            c.setTemperatura(Float.parseFloat(respuesta.get(keysArray.get(i)).toString()));
        }
    }

    private void obtenerCiudades() {
        String respuestaStr = "";
        JSONObject respuestaJSON = null;
        MessageParser parser = new MessageParser();
        JSONObject peticion = new JSONObject();
        peticion.put("ciudades", 4);

        try {
            Socket socketCliente = new Socket(ipServidorClima, puertoServidorClima);

            while (!respuestaStr.equals(Character.toString(MessageParser.ACKChar))) {
                escribeSocket(Character.toString(MessageParser.ENQChar), socketCliente);
                respuestaStr = leeSocket(socketCliente);
            }

            respuestaStr = "";

            while (respuestaJSON == null) {
                mandarPeticion(peticion, socketCliente);
                try {
                    respuestaJSON = parser.parseMessage(leeSocket(socketCliente));

                    guardarCiudades(respuestaJSON);
                } catch (MessageParserException e) {
                    respuestaJSON = null;
                }
            }

            while (!respuestaStr.equals(Character.toString(MessageParser.EOTChar))) {
                escribeSocket(Character.toString(MessageParser.EOTChar), socketCliente);
                respuestaStr = leeSocket(socketCliente);
            }
            
            socketCliente.close();
        } catch (UnknownHostException e) {
            System.out.println("No se ha encontrado el servidor del clima. Socket no abierto.");
        } catch (IOException e) {
            System.out.println("No se ha podido usar la conexión con el servidor del clima.");
        }

        
    }

    @Override
    public void run() {
        obtenerCiudades();

        try {
            authThread.join();


        } catch (InterruptedException e) {
            System.out.println("Se ha interrumpido el thread de Autenticación.");
        }
    }
    
}
