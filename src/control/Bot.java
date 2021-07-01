package control;

import INF1771_GameClient.Dto.PlayerInfo;
import INF1771_GameClient.Dto.ScoreBoard;
import INF1771_GameClient.Dto.ShotInfo;
import INF1771_GameClient.Socket.HandleClient;
import control.enums.Action;
import control.enums.Observation;
import control.interfaces.IBot;
import control.server.ConnectionListener;
import control.server.GameListener;
import control.server.Sender;

import graphics.View;

import javax.swing.*;
import java.util.*;

public class Bot implements Runnable, IBot {
    public HandleClient client;                             // client para conexao
    public Map<Long, PlayerInfo> listaJogadores;            // lista dos jogadores
    public List<ShotInfo> listaDisparos;                    // lista de disparos
    public List<ScoreBoard> listaScores;                    // lista de scores
    public Log log;                                         // log do jogo
    public Sender sender;                                   // envio de mensagens
    public long time;                                       // tempo decorrido
    public ArrayList<Observation> ultimaObservacao;         // observacao do bot

    public AI ai;

    public int x;
    public int y;
    public int tick;
    public PlayerInfo.Direction dir;
    public PlayerInfo.State state;
    public long score;
    public int energy;

    public JFrame tela;

    private void config() {
        this.client = new HandleClient();
        Field.init();
        this.log = new Log();
        this.sender = new Sender(this.client);

        this.ai = new AI(this);

        this.listaJogadores = new HashMap<>();
        this.listaDisparos = new ArrayList<>();
        this.listaScores = new ArrayList<>();
        this.ultimaObservacao = new ArrayList<>();

        this.time = 0;

        // cria um listener de conexao, e adiciona no client
        this.client.addChangeStatusListener(new ConnectionListener(this.client));

        // cria um listener de commando, passando o bot para atualizar minhas variaveis
        this.client.addCommandListener(new GameListener(this));

        // inicia a conexao
        this.client.connect(Config.url);
        this.sender.pedirNovoNome(Config.nomeJogador);
        this.sender.pedirCor(Config.corDefault);
        this.sender.pedirStatus();      // para carregar a posicao e direção logo

        tela = new View(this);

        Thread thread = new Thread(this);
        thread.start();
    }

    /**
     * Cria um novo bot, rodando as configuracoes padrao
     */
    public Bot() {
        config();
    }

    /**
     * Cria um novo bot, rodando as configuracoes padrao mas trocando o nome
     * @param nome Nome do bot
     */
    public Bot(String nome) {
        config();
        this.sender.pedirNovoNome(nome);
    }

    public void dormir(int ms) {
        if (ms < 0) { return; }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void limparObservacaoes() {
        this.ultimaObservacao.clear();
    }

    public void exibirScore() {
        System.out.println("==== SCOREBOARD ====");
        System.out.printf("Time: %d\n", time);
        System.out.printf("Status: %s\n", state.toString());
        for (ScoreBoard sb: this.listaScores) {
            String s = String.format("%s (%s): score=%d, energy=%d",
                    sb.name,
                    sb.connected ? "connected" : "not connected",
                    sb.score,
                    sb.energy);
            System.out.println(s);
        }
    }

    /* IMPLEMENTACOES DE IBOT */
    public int getX() { return this.x; }
    public int getY() { return this.y; }
    // public long getScore() { return this.score; }
    public int getEnergy() { return this.energy; }
    public int getTick() { return this.tick; }
    public PlayerInfo.Direction getDir() { return this.dir; }
    public ArrayList<Observation> getUltimaObservacao() { return this.ultimaObservacao; }
    /* FIM DAS IMPLEMENTACOES DO IBOT */

    /**
     * Roda o bot
     */
    public void run() {
        int timer = 0;

        this.sender.pedirStatusGame();
        dormir(Config.timerRapido);

        while (true) {
            this.tick += 1;
            this.sender.pedirStatusGame();
            switch (state) {
                // dentro de um jogo
                case game -> {
                    this.ai.atualizarMapa();
                    this.tela.repaint();
                    System.out.println(ultimaObservacao);

                    Action acao = this.ai.pensarRoubado();
                    this.sender.enviarAction(acao);

                    limparObservacaoes();
                    this.sender.pedirObservacao();
                    this.sender.pedirStatus();

                    dormir(Config.timerRapido);
                }
                case dead, ready, gameover -> {
                    Field.init();
                    if (timer == 5) {
                        this.sender.pedirScoreboard();
                        exibirScore();
                        timer = 0;
                    }
                    timer += 1;
                    dormir(Config.timerDefault);
                }
            }
            this.log.printLast();
        }
    }
}
