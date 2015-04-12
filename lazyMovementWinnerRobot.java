package ufba.winners;
import robocode.*;
import java.util.*;

// Nosso robô legal que vai ganhar
public class WinnerRobot extends AdvancedRobot {

	int speedFactor = 1;
	boolean borderSentryStatus = false;

	// Um gerador randômico para uso geral
	Random rand = new Random();

	// Dados sobre o inimigo em um dado instante de tempo
	class EnemyKnowledge {
		long time; // O número do turno de quando a informação foi obtida
		
		// Vetor posição:
		double x;
		double y;
		
		// Vetor velocidade:
		double speedX;
		double speedY;

		// Energia:
		double energy;
	}

	// Armazene uma lista das últimas N posições do inimigo
	// vai descartar informação antiga para preservar memória
	// Será útil para tentar prever o padrão de movimento inimigo
	// Estrutura é um Deque, insere em uma ponta (mais recente)
	// e vai descartando da outra ponta (mais antigos).
	final int knowledgeMaxAge = 20; // Idade máxima
	Deque<EnemyKnowledge> knowledgeDatabase = new LinkedList<EnemyKnowledge>();
	
	// Informação sobre o meu próprio robô no turno atual
	RobotStatus myself;

	// Toma a ação referente ao radar para o próximo turno
	void radarAction() {
		// Enquanto houverem inimigos e o mais antigo deles for mais antigo que a idade máxima...
		// ... remova o mais antigo
		while (knowledgeDatabase.size() > 0 && getTime() - knowledgeDatabase.peek().time > knowledgeMaxAge)
			knowledgeDatabase.pop();
			
		// Prever onde o inimigo estará baseado em dados anteriores
		EnemyKnowledge enemy = predict(getTime()+1);
	
		// Se não sabemos nada sobre o inimigo, girar o radar o mais rápido possível
		if (enemy == null) {
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
		double enemyAngle = enemyAngleFromMyself(enemy);
		double adjust = (rand.nextDouble() - 0.5) * 10; // Um valor entre -5 e 5
		setTurnRadarRight((enemyAngleFromMyself(enemy) - getRadarHeading() + 180) % 360 - 180 + adjust);
	}
	
	// Mirar no inimigo sempre
	void gunAction() {
		// TODO: Alterar o firePower a depender da chance de acertar
		double firePower = 2;
		double bulletSpeed = 20 - firePower * 3;
		
		// Resolução de um problema dinâmico:
		// Inicialmente assumir que o tiro vai demorar um turno para chegar no inimigo
		EnemyKnowledge enemy = null;
		long time = 1;
		
		// Repetidamente... (30 é um número arbitrário)
		for (int i = 0; i < 30; ++i) {
			// Calcular onde o inimigo vai estar quando o tiro chegar nele
			enemy = predict(getTime()+time);
			if (enemy == null) return; // não foi possível prever
			
			// Calcular quanto tempo vai levar para atingir onde ele vai estar
			time = (long)(enemyDistanceFromMyself(enemy) / bulletSpeed);
		}
		
		// Mirar na melhor direção para atirar
		double shotAngle = enemyAngleFromMyself(enemy);
		setTurnGunRight((shotAngle - getGunHeading() + 180) % 360 - 180);
		
		// Se já estamos bem perto dessa direção...
		if (Math.abs(shotAngle - getGunHeading()) < 3)
			setFire(firePower);
	}

	// Execução principal do robô. Vai chamar as funções referentes a cada
	// componente e depois executar tudo em loop.
	public void run() {
		while (true) {
			radarAction();
			gunAction();
			execute();
			circleMovement();
		}
	}
	
	// Retorna o angulo para onde apontar
	double enemyAngleFromMyself(EnemyKnowledge enemy) {
		double dx = enemy.x - myself.getX();
		double dy = enemy.y - myself.getY();
		double angle = Math.toDegrees(Math.atan2(dx, dy));
		if (angle < 0) angle += 360;
		return angle;
	}
	
	// Retorna a distancia para o inimigo
	double enemyDistanceFromMyself(EnemyKnowledge enemy) {
		double dx = enemy.x - myself.getX();
		double dy = enemy.y - myself.getY();
		return Math.hypot(dx, dy);
	}
	
	// Deve prever onde o inimigo vai estar no tempo dado
	// Chave para o sucesso :)
	EnemyKnowledge predict(long time) {
		// Se não temos dados do radar, não podemos prever
		if (knowledgeDatabase.size() == 0) {
			return null;
		}
		
		EnemyKnowledge last = knowledgeDatabase.peekLast();
		long dt = time - last.time; // Variação no tempo desde a última informação
		
		EnemyKnowledge enemy = new EnemyKnowledge();
		enemy.time = time; // É como se fosse uma informação do radar do futuro
		
		// Assumir que foi em linha reta
		enemy.x = last.x + last.speedX * dt;
		enemy.y = last.y + last.speedY * dt;
		
		// Assumir que não mudou de velocidade
		enemy.speedX = last.speedX;
		enemy.speedY = last.speedY;
		
		// Se estamos prevendo ele fora do ring, é porque ele vai bater em
		// uma parede. Não sabemos o que fará depois de bater na parede :(
		if (enemy.x < 0 || enemy.y < 0 ||
		    enemy.x > getBattleFieldWidth() || enemy.y > getBattleFieldHeight())
			return null;
		
		return enemy;
	}
	
	// Sempre que radar notar um robô, adicionar
	// ele na lista de informações do inimigo
	public void onScannedRobot(ScannedRobotEvent e) {
		EnemyKnowledge enemy = new EnemyKnowledge();
		knowledgeDatabase.add(enemy);

		enemy.time = getTime();
		
		double angle = getHeading() + e.getBearing();
		enemy.x = myself.getX() + Math.sin(Math.toRadians(angle)) * e.getDistance();
		enemy.y = myself.getY() + Math.cos(Math.toRadians(angle)) * e.getDistance();
		enemy.speedX = Math.sin(Math.toRadians(e.getHeading())) * e.getVelocity();
		enemy.speedY = Math.cos(Math.toRadians(e.getHeading())) * e.getVelocity();
		enemy.energy = e.getEnergy();
	}

	// Em todos os turnos, obter informações sobre meu robô
	public void onStatus(StatusEvent e) {
		myself = e.getStatus();
	}

	// Movimento circular do robô
	public void circleMovement(){
		setTurnRight(10000);
		setAhead(10000*speedFactor);
	}

	// Movimento preguiçoso do robô (se move se houver perigo)
	public void lazyMovement() {
		if(!borderSentryStatus) {
			EnemyKnowledge enemy1, enemy2, enemy3;
			Iterator iterator = knowledgeDatabase.descendingIterator();
			enemy1 = (EnemyKnowledge) iterator.next(); // ultimo
			enemy2 = (EnemyKnowledge) iterator.next(); // penultimo
			enemy3 = (EnemyKnowledge) iterator.next(); // anti penultimo
			if(abs(abs(enemy1.x - enemy2.x) - abs(enemy2.x - enemy3.x)) < 0.1 &&
				abs(abs(enemy1.y - enemy2.y) - abs(enemy2.y - enemy3.y)) < 0.1 ) {	// é uma reta
					double enemyAngle = enemyAngleFromMyself(enemy);
				}
			else if(enemy1.energy < enemy2.energy) { // inimigo atirou

			}
		}
	}

	public float abs(float a) {		// bibilioteca math cade?
        return (a <= 0.0F) ? 0.0F - a : a;
    }

	// Muda direção ao atingir um robô inimigo
	public void onHitRobot(HitRobotEvent e) {
       speedFactor = speedFactor * -1;
   }
	// Vai para tras ao atingir uma parede
	 public void onHitWall(HitWallEvent event){
   		setTurnRight(0);
   		setBack(20000);
   }
}

