import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

class VoosManager {
    private HashMap<String,Utilizador> utilizadores;
    private HashMap<Integer,Reserva> reservas;
    private HashMap<Integer,Voo> voos;
    private ReentrantLock lock;
    private String utilizadoresCsv;
    private String voosCsv;
    private int lastidVoo;
    private int lastidReserva;

    public VoosManager(String utilizadoresCsv,String voosCsv) {
        lock = new ReentrantLock();
        utilizadores = new HashMap<>();
        reservas = new HashMap<>();
        voos = new HashMap<>();
        this.utilizadoresCsv = utilizadoresCsv;
        this.voosCsv = voosCsv;
        this.lastidVoo = 0;
        this.lastidReserva = 0;
    }

    public void updateUtilizadores(Utilizador u) {
        lock.lock();
        utilizadores.put(u.getNome(),u);
        lock.unlock();
    }

    public void updateReservas(Reserva r){
        lock.lock();
        lastidReserva++;
        r.setCodigo(lastidReserva);
        reservas.put(r.getCodigo(),r);
        //adiciona lotação NAO ESTA A ATUALIZAR
        for(int id : r.getViagem()){
            voos.get(id).addLotacao(1);
        }
        lock.unlock();
    }

    public void removeReserva(String codReserva){
        lock.lock();
        reservas.remove(codReserva);
        lock.unlock();
    }

    public void updateVoos(Voo v){
        lock.lock();
        lastidVoo++;
        v.setId(lastidVoo);
        voos.put(lastidVoo,v);
        lock.unlock();
    }
    public int existsVoo(String origem,String destino){
        for(Voo v : voos.values()){
            if(v.getOrigem().equals(origem) && v.getDestino().equals(destino)){
                return v.getId();
            }

        }
        return -1;
    }

    public boolean existeUtilizador(String name){
        return this.utilizadores.containsKey(name);
    }

    public VoosList getVoos () {
        try{
            lock.lock();
            VoosList ret = new VoosList();
            ret.addAll(voos.values());
            return ret;
        }
        finally {
            lock.unlock();
        }
    }

    public ArrayList<String> getReservasVoos(String nome){
        ArrayList<String> listaReservas = new ArrayList<>();
        StringBuilder sb;
        for(Reserva r : reservas.values()){
            int i = 0;
            sb = new StringBuilder();
            sb.append("#CodigoReserva ").append(r.getCodigo()).append(" Viagem: ");
            for(int idVoo : r.getViagem() ){
                Voo v = voos.get(idVoo);
                if(i==0){
                    sb.append(v.getOrigem());
                    sb.append("->");
                    sb.append(v.getDestino());
                }else{
                    sb.append("->");
                    sb.append(v.getDestino());
                }
                i++;
            }
            listaReservas.add(sb.toString());
        }
        return listaReservas;
    }

    public int getLastidVoo() {
        return lastidVoo;
    }
    public int getLastidReserva(){
        return lastidReserva;
    }

    public Utilizador getUtilizador(String nome) {
            return utilizadores.get(nome);
    }

    public void registoUtilizadorCsv(String nome,String password ,int adminPermission) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(this.utilizadoresCsv,true));
        bw.write("\n");
        bw.write(nome+";"+password+";"+adminPermission);
        bw.close();
    }

    public void registoVooCsv(String origem,String destino,int capacidade) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(this.voosCsv,true));
        bw.write("\n");
        bw.write(origem+";"+destino+";"+capacidade);
        bw.close();
    }

    public void loadUtilizadoresCsv() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(this.utilizadoresCsv));
        String line;
        while ((line = br.readLine()) != null){
            String[] parsed = line.split(";");
            updateUtilizadores(new Utilizador(parsed[0],parsed[1],Integer.parseInt(parsed[2])));
        }
        br.close();

    }

    public void loadVoosCsv() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(this.voosCsv));
        String line;
        while ((line = br.readLine()) != null){
            String[] parsed = line.split(";");
            updateVoos(new Voo(parsed[0],parsed[1],Integer.parseInt(parsed[2])));
        }
        br.close();

    }

    public void load() throws IOException {
        loadUtilizadoresCsv();
        loadVoosCsv();
    }
}

class Handler implements Runnable {
    private Socket socket;
    private VoosManager manager;
    private DataInputStream dis;
    private DataOutputStream dos;
    private Utilizador user = null;
    private boolean logged = false;
    private int idC;

    public Handler (Socket socket, VoosManager manager, int idC){
        this.socket = socket;
        this.manager = manager;
        this.idC = idC;
        try{
            this.dis = new DataInputStream(socket.getInputStream());
            this.dos = new DataOutputStream(socket.getOutputStream());
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    //quit -> 0
    //registo -> 1 + (naoadmin 0,admin 1)
    //login -> 2
    //logout -> 3
    //voos -> 4
    //reservas -> 5
    //reserva -> 6
    //cancela -> 7
    //encerra -> 8
    //addvoo -> 9

    public boolean quit() throws IOException {
        dis.close();
        dos.close();
        socket.close();
        return true;
    }

    public void registo() throws IOException {
        StringBuilder sb;
        sb = new StringBuilder();
        int code = dis.readInt();
        if(code == 1) {
            if (logged && user.isAdmin()) {
                String nome = dis.readUTF();
                String password = dis.readUTF();
                if (manager.existeUtilizador(nome)) {
                    sb.append("Erro: Nome de utilizador já existe no sistema!");
                } else {
                    manager.updateUtilizadores(new Utilizador(nome, password, 1));
                    manager.registoUtilizadorCsv(nome, password, 0);
                    sb.append("Utilizador registado com o nome ").append(nome).append(".");
                }
            } else {
                sb.append("Você não tem permissoes de admin");
            }
        }
        else if (code == 0) {
            if (logged) {
                String nome = dis.readUTF();
                String password = dis.readUTF();
                if (manager.existeUtilizador(nome)) {
                    sb.append("Erro: Nome de utilizador já existe no sistema!");
                } else {
                    manager.updateUtilizadores(new Utilizador(nome, password, 0));
                    manager.registoUtilizadorCsv(nome, password, 0);
                    sb.append("Utilizador registado com o nome ").append(nome).append(".");
                }
            } else {
                sb.append("Você ja se encontra logado!");
            }
        }
        else{
            sb.append("Erro");
        }
        dos.writeUTF(sb.toString());
    }

    public void logout() throws IOException {
        if(logged) {
            logged = false;
            user = null;
            dos.writeUTF("Deslogado com sucesso!");
        }
        else dos.writeUTF("Você não se encontra logado!");
        dos.flush();
    }

    public void login() throws IOException {
        StringBuilder sb;
        sb = new StringBuilder();
        if(!logged){
            String nome = dis.readUTF();
            String password = dis.readUTF();
            if(manager.existeUtilizador(nome)){
                user = manager.getUtilizador(nome);
                if(password.equals(user.getPassword())){
                    logged = true;
                    sb.append("Logado com sucesso!");
                }else{
                    sb.append("Erro: Password incorreta!");
                }
            }else {
                sb.append("Erro: Utilizador não existe no sistema, experimente registar primeiro!");
            }
        }else{
            sb.append("Vocé ja se encontra logado!");
        }
        dos.writeUTF(sb.toString());
        dos.writeBoolean(logged);
    }

    public void voos() throws IOException {
        if(logged) {
            VoosList voos = manager.getVoos();
            voos.serialize(dos);
        }
        else{
            VoosList voos = new VoosList();
            voos.serialize(dos);
        }
    }

    public void reservas() throws IOException {/*todo listar reservas do utilizador logado*/
       ArrayList<String> rs = manager.getReservasVoos(user.getNome());
       dos.writeInt(rs.size());
       for(String s : rs){
           dos.writeUTF(s);
       }
    }

    public void reserva() throws IOException {
        if(logged) {
            String[] viagem = dis.readUTF().split(";");
            String[] datas = dis.readUTF().split(";");
            boolean valido = true;
            List<Integer> idsVoos = new ArrayList<>();
            List<LocalDate> datasVoos = new ArrayList<>();
            //todo: falta verifica lotação
            if (viagem.length >= 2) {
                for (int i = 0; i < viagem.length - 1 && valido; i++) {
                    int id;
                    if ((id = manager.existsVoo(viagem[i], viagem[i + 1])) != -1) {
                        idsVoos.add(id);
                    } else {
                        valido = false;
                    }

                    for(String d : datas){
                        datasVoos.add(LocalDate.parse(d));
                    }
                }
                //neste momento está a escolher a primeira data possivel
                if(valido){
                    manager.updateReservas(new Reserva(idsVoos,datasVoos.get(0),user.getNome()));
                    dos.writeUTF("Viagem reservada com sucesso.");
                }else{
                    dos.writeUTF("Não foi possivel concluir reserva");
                }
            }
        }
    }

    public void cancela() throws IOException {
        if(logged) {
            String codReserva = dis.readUTF();
            //todo: verificar se o user loggado que fez a reserva
            manager.removeReserva(codReserva);
            dos.writeUTF("Reserva " + codReserva + " cancelada");
        }
    }

    public void encerra(){/*todo admin encerrar dia*/}

    public void addvoo() throws IOException {
        StringBuilder sb;
        sb = new StringBuilder();
        if(logged && user.isAdmin()) {
            boolean validoOD = true;
            boolean validoC = true;
            String origem = dis.readUTF();
            String destino = dis.readUTF();
            String Scapacidade = dis.readUTF();
            if (origem.equals(destino)) {
                validoOD = false;
            }
            try {
                int capacidade = Integer.parseInt(Scapacidade);
                int id = manager.getLastidVoo() + 1;
                if (capacidade < 100 || capacidade > 250) {
                    validoC = false;
                }
                if (validoC && validoOD) {
                    manager.updateVoos(new Voo(origem, destino, capacidade));
                    manager.registoVooCsv(origem,destino,capacidade);
                    sb.append("O voo ").append(origem).append(" -> ").append(destino).append(" com a capacidade de ").append(capacidade).append(" passageiros, foi registado com o id: ").append(id).append(".");
                } else if (!validoC) {
                    sb.append("Erro ao registar voo: ").append(capacidade).append(" não é uma capacidade válida, experimente [100-250]");
                } else
                    sb.append("Erro ao registar voo: Origem e destino inválidos");
            } catch (NumberFormatException e) {
                sb.append("Erro ao registar voo: ").append(Scapacidade).append(" não é uma capacidade válida");
            } finally {
                dos.writeUTF(sb.toString());
                dos.flush();
            }
        }else{
            sb.append("Não tem permissão para adicionar voo");
            dos.writeUTF(sb.toString());
            dos.flush();
        }

    }

    public void run() {
            try{
                boolean finish = false;
                while(!finish) {
                    int opcode = dis.readInt();
                    System.out.println("opcode recebido do cliente "+idC+": "+opcode);
                    switch (opcode){
                        case 0 -> finish =  quit();
                        case 1 -> registo();
                        case 2 -> login();
                        case 3 -> logout();
                        case 4 -> voos();
                        case 5 -> reservas();
                        case 6 -> reserva();
                        case 7 -> cancela();
                        case 8 -> encerra();
                        case 9 -> addvoo();
                        default -> {}
                    }
                }
            }
            catch(IOException e){
                e.printStackTrace();
            }
    }
}

public class Server{

    public static void main (String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(12345);
        VoosManager manager = new VoosManager("../ProjetoSD/cp/registos.csv",
                                                "../ProjetoSD/cp/voos.csv");//os ficheiros de persistencia são passados na criação do manager
        manager.loadUtilizadoresCsv();
        manager.load();
        int i = 0;

        while (true) {
            Socket socket = serverSocket.accept();
            i++;
            Thread handler = new Thread(new Handler(socket, manager,i));
            handler.start();
        }
    }

}
