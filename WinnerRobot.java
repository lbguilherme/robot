package ufba.winners;
import robocode.*;
import java.util.*;
import java.awt.*;

// Nosso robô legal que vai ganhar
public class WinnerRobot extends AdvancedRobot {

	int movementCount = 0;
	int wallevent = 0;

	// Um gerador randômico para uso geral
	Random rand = new Random();

	// Dados sobre o inimigo em um dado instante de tempo
	class TankInfo {
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
	final int infoMaxAge = 20; // Idade máxima
	Deque<TankInfo> infoDatabase = new LinkedList<TankInfo>();
	
	// Informação sobre o meu próprio robô no turno atual
	TankInfo myself = new TankInfo();

	// Toma a ação referente ao radar para o próximo turno
	void radarAction() {
		// Enquanto houverem inimigos e o mais antigo deles for mais antigo que a idade máxima...
		// ... remova o mais antigo
		while (infoDatabase.size() > 0 && getTime() - infoDatabase.peek().time > infoMaxAge)
			infoDatabase.pop();
			
		// Prever onde o inimigo estará baseado em dados anteriores
		TankInfo enemy = predict(getTime()+1);
	
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
		//double enemyAngle = enemyAngleFromMyself(enemy);
		double adjust = (rand.nextDouble() - 0.5) * 10; // Um valor entre -5 e 5
		setTurnRadarRight(fixAngle(enemyAngleFromMyself(enemy) - getRadarHeading() + adjust));
	}
	
	double fixAngle(double angle) {
		while (angle < -180) angle += 360;
		while (angle > 180) angle -= 360;
		return angle;
	}
	
	// Mirar no inimigo sempre
	void gunAction() {
		// Resolução de um problema dinâmico:
		// Inicialmente assumir que o tiro vai demorar um turno para chegar no inimigo
		TankInfo enemy = predict(getTime()+1);
		if (enemy == null) return;
		
		long time = 1;
		
		double firePower = 0.1 + Math.pow(enemyDistanceFromMyself(enemy), 0.2);
		double bulletSpeed = 20 - firePower * 3;
		
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
		setTurnGunRight(fixAngle(shotAngle - getGunHeading()));
		
		// Se já estamos bem perto dessa direção...
		if (Math.abs(fixAngle(shotAngle) - fixAngle(getGunHeading())) < 2)
			setFire(firePower);
	}

	// Execução principal do robô. Vai chamar as funções referentes a cada
	// componente e depois executar tudo em loop.
	public void run() {

		setBodyColor(Color.black);
		setGunColor(Color.black);
		setRadarColor(Color.black);

		while (true) {
			/*
			addCustomEvent(new Condition("ImaginaryWallHit") {
				public boolean test() {
					if((getBattleFieldHeight() - myself.y) < 50 || (getBattleFieldWidth() - myself.x) < 50) {
						out.println("Imaginry Wall Hit Condition met.");
						return true;
					}
					return false;
				}
			});
			*/
			radarAction();
			gunAction();
			lazyMovement();
			execute();
		}
	}
	
	// Retorna o angulo para onde apontar
	double enemyAngleFromMyself(TankInfo enemy) {
		double dx = enemy.x - myself.x;
		double dy = enemy.y - myself.y;
		double angle = Math.toDegrees(Math.atan2(dx, dy));
		//if (angle < 0) angle += 360;
		return angle;
	}
	
	// Retorna a distancia para o inimigo
	double enemyDistanceFromMyself(TankInfo enemy) {
		double dx = enemy.x - myself.x;
		double dy = enemy.y - myself.y;
		return Math.hypot(dx, dy);
	}
	
	// Deve prever onde o inimigo vai estar no tempo dado
	// Chave para o sucesso :)
	TankInfo predict(long time) {
		// Se não temos dados do radar, não podemos prever
		if (infoDatabase.size() == 0) {
			return null;
		}
		
		TankInfo last = infoDatabase.peekLast();
		long dt = time - last.time; // Variação no tempo desde a última informação
		
		TankInfo enemy = new TankInfo();
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
		TankInfo enemy = new TankInfo();
		infoDatabase.add(enemy);

		enemy.time = getTime();
		
		double angle = getHeading() + e.getBearing();
		enemy.x = myself.x + Math.sin(Math.toRadians(angle)) * e.getDistance();
		enemy.y = myself.y + Math.cos(Math.toRadians(angle)) * e.getDistance();
		enemy.speedX = Math.sin(Math.toRadians(e.getHeading())) * e.getVelocity();
		enemy.speedY = Math.cos(Math.toRadians(e.getHeading())) * e.getVelocity();
		enemy.energy = e.getEnergy();
	}

	// Em todos os turnos, obter informações sobre meu robô
	public void onStatus(StatusEvent e) {
		myself.time = getTime();
		myself.x = e.getStatus().getX();
		myself.y = e.getStatus().getY();
	}

	// Movimento preguiçoso do robô (se move se houver perigo)
	public void lazyMovement() {
		if (infoDatabase.size() >= 3) {
			Iterator iterator = infoDatabase.descendingIterator();
			TankInfo enemy1 = (TankInfo) iterator.next(); // ultimo
			TankInfo enemy2 = (TankInfo) iterator.next(); // penultimo
			if (Math.abs(enemy1.energy - enemy2.energy) > 0.09) { // inimigo atirou
				movementCount += 1;
				if (movementCount > 3) {
					movementCount = 0;
					setTurnRight(90);
					setAhead(100);
				} else{
					setAhead(100);
				}
			}
		}
	}
	
	// Vai para tras ao atingir uma parede
	public void onHitWall(HitWallEvent event){
   		setBack(500);
	}
	/*
	public void onCustomEvent(CustomEvent event){
		if (event.getCondition().getName().equals("ImaginaryWallHit")) {
			out.println("On Custom event rechead.");
			setBack(400);
		}
	}
	*/
}
