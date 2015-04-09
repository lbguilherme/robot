package ufba.winners;
import robocode.*;
import java.util.*;

// Nosso robô legal que vai ganhar
public class WinnerRobot extends AdvancedRobot {

	// Um gerador randômico para uso geral
	Random rand = new Random();

	// Dados sobre o inimigo em um dado instante de tempo
	class EnemyKnowledge {
		long time; // O número do turno de quando a informação foi obtida
		double angle; // Direção absoluta
	}

	// Armazene uma lista das últimas N posições do inimigo
	// vai descartar informação antiga para preservar memória
	// Será útil para tentar prever o padrão de movimento inimigo
	// Estrutura é um Deque, insere em uma ponta (mais recente)
	// e vai descartando da outra ponta (mais antigos).
	final int knowledgeMaxAge = 20; // Idade máxima
	Deque<EnemyKnowledge> knowledgeDatabase = new LinkedList<EnemyKnowledge>();

	// Toma a ação referente ao radar para o próximo turno
	void radarAction() {
		// Enquanto houverem inimigos e o mais antigo deles for mais antigo que a idade máxima...
		// ... remova o mais antigo
		while (knowledgeDatabase.size() > 0 && getTime() - knowledgeDatabase.peek().time > knowledgeMaxAge)
			knowledgeDatabase.pop();
	
		// Se não sabemos nada sobre o inimigo, girar o radar o mais rápido possível
		if (knowledgeDatabase.size() == 0) {
			setTurnRadarRight(32); // Bom número cálculado pela razão de ouro
			return;
		}
		
		// Faz o radar apontar para a última posição conhecida do inimigo
		// O fator aleatório introduz um pequeno movimento do radar que
		// ajuda a capturar movimento no inimigo.
		// Esse método mantém o radar apontado para o inimigo e gera um novo
		// evento onScannedRobot() práticamente em todos os turnos
		// Em meus testes, apenas 2 a cada 100 turnos não receberam o evento
		// Isso vai manter um bom fluxo de novas informações
		EnemyKnowledge enemy = knowledgeDatabase.peekLast();
		double adjust = (rand.nextDouble() - 0.5) * 10;
		setTurnRadarRight(enemy.angle - getRadarHeading() + adjust);
	}

	// Execução principal do robô. Vai chamar as funções referentes a cada
	// componente e depois executar tudo em loop.
	public void run() {
		while (true) {
			radarAction();
			execute();
		}
	}
	
	// Sempre que radar notar um robô, adicionar
	// ele na lista de informações do inimigo
	public void onScannedRobot(ScannedRobotEvent e) {
		EnemyKnowledge enemy = new EnemyKnowledge();
		knowledgeDatabase.add(enemy);
		
		enemy.time = getTime();
		enemy.angle = getHeading() + e.getBearing();
	}
	
}
