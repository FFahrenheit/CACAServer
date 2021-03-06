/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cacaserver.requests;

import cacaserver.controller.Context;
import cacaserver.database.Database;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author manie
 */
public class NewGroupMessage {

    private String remitente;
    private String gId;
    private String mssg;
    private static Logger logger;
    private Connection connection;
    private Context context;
    private Socket sender;

    static {
        logger = Logger.getLogger("NewGroup");
    }
    
    /**
     * 
     * @param args
     * @param sender
     * @param context 
     * Esta clase recibe los datos enviados por el cliente 
     * y llama newGroupMssg para intentar insertar un nuevo 
     * mensaje en la base de datos. En caso de que la 
     * inserción haya sido exitosa, se obtiene la lista de
     * todos los usuarios registrados en el grupo por medio de
     * la función getUsers para, posteriormete enviarles a todos
     * la repsuesta que contiene el mensaje, el remitente y el 
     * id del grupo.
     */
    public NewGroupMessage(JsonObject args, Socket sender, Context context) {
        try {
            this.sender = sender;
            this.remitente = args.get("remitente").getAsString();
            this.gId = args.get("groupId").getAsString();
            this.mssg = args.get("mssg").getAsString();
            connection = Database.getConnection();

            JsonObject response = new JsonObject();
            response.addProperty("type", "newGroupMssg");

            if (newGroupMssg()) {
                response.addProperty("status", true);

                JsonObject msj = new JsonObject();
                msj.addProperty("id", this.gId);
                msj.addProperty("mssg", this.mssg);
                msj.addProperty("remitente", this.remitente);
                response.add("args", msj);

                logger.info("New group message registered with status code " + response.get("status").getAsBoolean());

                Gson gson = new Gson();

                String envio = gson.toJson(response);

                sender.getOutputStream().write(envio.getBytes()); //Se envía al remitente para actualizar su chatsin

                Database.returnConnection(connection);
                
                ArrayList<String> users = getUsers();
                
                Hashtable<Socket, String> connected = context.getConnectedUsers();

                synchronized(connected)
                {
                    connected.forEach((socket, user )->
                    {
                        if((users.contains(user) && !socket.equals(this.sender)))
                        {
                            try 
                            {
                                socket.getOutputStream().write(envio.getBytes());
                                
                            } catch (IOException ex) {
                                Logger.getLogger(NewPersonalMssg.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }

                    });
                    connected.notify();
                }
                 
            }

        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage());
        }
    }
    
    /**
     * Esta funcion es usada para intentar insertar un nuevo
     * mensaje en la base de datos con los datos enviados por
     * el cliente. 
     * @return 
     * La clase retorna verdadero en caso de que la operación
     * con la base de datos haya sido exitosa y falso en el 
     * caso contrario.
     */
    private boolean newGroupMssg() {
        connection = Database.getConnection();

        try {

            String query;
            query = "INSERT INTO chatgrupo(mensaje, remitente, grupo) VALUES ('" + mssg + "','" + remitente + "'," + gId + ")";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.execute();
            return true;
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, ex.getMessage());
            return false;
        }
    }

    /**
     * Esta función es usada para realizar una consulta a la 
     * base de datos, la cual consulta todos los usuarios
     * registrados en el grupo que la clase esté manipulando.
     * @return 
     * La clase retorna todos los usuarios de un grupo por 
     * medio de un ArrayList de Strings.
     */
    private ArrayList<String> getUsers() {
        try {
            ArrayList<String> users = new ArrayList<String>();
            connection = Database.getConnection();
            
            String query;
            query = "SELECT usuario FROM miembro WHERE grupo ="+gId;
            PreparedStatement stmt = connection.prepareStatement(query);
            ResultSet result = stmt.executeQuery();
            
            while(result.next()){
                users.add(result.getString("usuario"));
            }
            
            return users;
            
        } catch (SQLException ex) {
            Logger.getLogger(NewGroupMessage.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }           
    }
}
