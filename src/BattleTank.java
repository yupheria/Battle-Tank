import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.*;
import javax.microedition.io.*;
import javax.bluetooth.*;
import java.io.*;
import java.util.*;

public class BattleTank extends MIDlet implements CommandListener{
	public Display display;
	final String uuid = "0123456789abcdef0123456789abcdef";
	StreamConnectionNotifier scn;	
	DataInputStream in;
	DataOutputStream out;
	int rows, cols, rowOffset, colOffset;
	BattleS s, c;
	boolean isServer;
	
	protected void startApp() {
		display = Display.getDisplay(this);
		menu();
	}	
	
	protected void pauseApp() {
	}
	
	protected void destroyApp(boolean unc) {
		notifyDestroyed();
	}
	
	public void quit() {
		destroyApp(true);
	}
	void menu() {
		List list = new List(
			"BattleTank", 
			Choice.IMPLICIT, 
			new String[] {"Create Game", "Join Game","Mechanics","About", "Exit"}, 
			null);
		list.setCommandListener(this);
		display.setCurrent(list);
	}
	public Alert about(){
		Alert about =  new Alert("ABOUT","MEMEBERS: ABING, Silvyster, GROENBERG, Bjoern, ONG, Brian, SAMSON, Froilan, SUMCAD, Redford, VILLANUEVA, Danilo",null, AlertType.INFO);
		about.setTimeout(-2);
		return about;
	}
	public Alert mechanics(){
		String m = "Controlling the tank will be by pressing UP,DOWN,LEFT or RIGHT and FIRE to fire a missile from your tank on which the tank is facing.";
		String m1 = " When hit by a missile, a heart will be reduced to the player and if all 3 hearts are gone the oppossing player wins a flag and both players will have 3 lives again.";
		String m2 = " When a missile hit an eagle, the player who destroyed the oppssing player's eagle eagle wins a flag.";
		String m3 = " The first player who wins 3 flags, wins the game";
		Alert mechanics = new Alert("MECHANICS",m+m1+m2+m3,null,AlertType.INFO);
		mechanics.setTimeout(-2);
		return mechanics;
	}
	public void commandAction(Command cmd, Displayable disp) {
		List list = (List) disp;
		switch (list.getSelectedIndex()) {
			case 0:
				TankServer server = new TankServer(this);
				(new Thread(server)).start();
				break;
			case 1:
				TankClient client = new TankClient(this);
				(new Thread(client)).start();
				break;
			case 2: 
				display.setCurrent(mechanics());
				break;
			case 3:
				display.setCurrent(about());
				break;
			case 4:
				notifyDestroyed();
				break;
		}
	}
	
	class TankClient implements Runnable, CommandListener {
		Command cmdCancel;
		BattleTank main;
		public TankClient(BattleTank main){
			this.main = main;
		}
		public void run() {
			isServer = false;
			Form form = new Form("BattleTank Client");
			form.append("Searching for opponent...");
			cmdCancel = new Command("Cancel", Command.CANCEL, 0);
			form.addCommand(cmdCancel);
			form.setCommandListener(this);	
			display.setCurrent(form);
			
			try {
				LocalDevice ld = LocalDevice.getLocalDevice();
				DiscoveryAgent da = ld.getDiscoveryAgent();
				String conStr = da.selectService(new UUID(uuid, false), ServiceRecord.AUTHENTICATE_ENCRYPT, false);
				StreamConnection clientCon = (StreamConnection) Connector.open(conStr);
				in = clientCon.openDataInputStream();
				out = clientCon.openDataOutputStream();
				
				form.delete(0);
				form.append("Connected to opponent.\n");
				form.append("You will be the Red Tank.\n");
				form.append("Waiting for game start.");
				
				String sg = in.readUTF();
				//if(sg.charAt(0) == 'S') {
				if(sg.equals("S")) {
					BattleS c = new BattleS(false,main);
					(new Thread(c)).start();
					display.setCurrent(c);
				}
				
			} catch (Exception e) {};
		}
		
		public void commandAction(Command cmd, Displayable disp) {
			menu();
		}
}
class TankServer implements  Runnable, CommandListener {
		Command cmdCancel, cmdStart;
		BattleTank main;
		public TankServer(BattleTank main){
			this.main = main;
		}
		public void run() {
			isServer = true;
			Form form = new Form("BattleTank Server");
			form.append("Waiting for opponent...");
			cmdCancel = new Command("Cancel", Command.CANCEL, 0);
			form.addCommand(cmdCancel);
			form.setCommandListener(this);	
			display.setCurrent(form);
			
			try {
				LocalDevice ld = LocalDevice.getLocalDevice();
				ld.setDiscoverable(DiscoveryAgent.GIAC);
				scn = (StreamConnectionNotifier) Connector.open("btspp://localhost:" + uuid);
				StreamConnection serverCon = scn.acceptAndOpen();
				in = serverCon.openDataInputStream();
				out = serverCon.openDataOutputStream();
				
				form.delete(0);
				form.append("Opponent connected.\n");
				form.append("You will be the White Tank.\n");
				cmdStart = new Command("Start", Command.OK, 0);
				form.addCommand(cmdStart);
			} catch (Exception e) {}
		}
		public void commandAction(Command cmd, Displayable disp) {
			if (cmd == cmdCancel) {
				try {
					scn.close();
				} catch (Exception e) {}
				
				menu();
				return;
			} 
			
			if (cmd == cmdStart) {
				try {
				out.writeUTF("S");
				out.flush();
				} catch(Exception e) {}
				BattleS s = new BattleS(true,main);
				(new Thread(s)).start();
				display.setCurrent(s);
			}
		}
	}
class BattleS extends GameCanvas implements Runnable {
	boolean server, over = false;
	private final int BLACK  = 0x000000;
	private final int WHITE  = 0xFFFFFF;
	private final int TOPHCENTER = Graphics.TOP | Graphics.HCENTER;
	LayerManager lm;
	private Graphics g;
    int count=0;
    Sprite tank,tank1,missile;
    Vector tanks,missiles0,missiles1,missiles2,missiles3,booms,bricks,eagles,metals,forests,waters;    
    int dir=0;
    Sprite water,forest,brick,boom,eagle1,eagle2,metal,heart,heart1,flag,flag1;  
    int direction;
    Font fontLarge;
    int flHalf;
	boolean winner = false;
	BattleTank main;
	int wins = 0;
	int wins1 = 0;
	public BattleS(boolean server, BattleTank main) {
		super(false);
		this.main = main;
		Font fontLarge = Font.getFont(Font.FACE_PROPORTIONAL,
                                      Font.STYLE_BOLD,
                                      Font.SIZE_LARGE);
        int flHalf = fontLarge.getHeight() / 2;
		this.server = server;	
			
		g = getGraphics();
		setFullScreenMode(true);
    	
        g.setColor(BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());  
        	
         lm = new LayerManager();

		flag = sprFlag();
		flag.setRefPixelPosition(getWidth(),getHeight());;
		lm.insert(flag,0);
		
		flag1 = sprFlag();
		flag1.defineReferencePixel(0, 0);
		flag1.setRefPixelPosition(0,0);
		lm.insert(flag1,0);

         tanks = new Vector();
         tank = sprTank();
         tank.setRefPixelPosition((getWidth()/4)*3, getHeight()-26);
         lm.insert(tank, 0);  
         tanks.addElement(tank);
         
         tank1 = sprTank1();
         tank1.setRefPixelPosition(getWidth()/4, 26);        
         lm.insert(tank1, 0);           
         tanks.addElement(tank1);          
         
          bricks = new Vector();
          eagles = new Vector();
          metals = new Vector();
          forests = new Vector();
          waters = new Vector();
          
          missiles0 = new Vector();
          missiles1 = new Vector();	 
          missiles2 = new Vector();	 
          missiles3 = new Vector();	      	 
          missile = sprMissile();
                    
          booms = new Vector();
          
          heart = sprHeart();
          heart.setRefPixelPosition(0,getHeight());
          lm.insert(heart,0);         
         
          heart1 = sprHeart();
          heart1.setRefPixelPosition(getWidth()-45,14);
          lm.insert(heart1,0);       
          	
          eagle1 = sprEagle();
          eagle1.setRefPixelPosition(getWidth()/2,getHeight()-8);
          lm.insert(eagle1,0);          
          eagles.addElement(eagle1);
          
          eagle2 = sprEagle();
          eagle2.setRefPixelPosition(getWidth()/2,10);
          lm.insert(eagle2,0);
          eagles.addElement(eagle2);              
          
          brick = sprBrick(); // d down
          brick.setRefPixelPosition((getWidth()/2)+8,getHeight()-12);
          lm.insert(brick,0);
          bricks.addElement(brick);
          
          brick = sprBrick(); //d down
          brick.setRefPixelPosition((getWidth()/2)-24,getHeight()-12);
          lm.insert(brick,0);
          bricks.addElement(brick);
          
          brick = sprBrick(); // d up
          brick.setRefPixelPosition((getWidth()/2)+8,4);
          lm.insert(brick,0);
          bricks.addElement(brick);
          
          brick = sprBrick(); //d up
          brick.setRefPixelPosition((getWidth()/2)-24,4);
          lm.insert(brick,0);
          bricks.addElement(brick);
          
          metal = sprMetal();    //defense for eagle down
          metal.setRefPixelPosition((getWidth()/2)-8,getHeight()-32);
          lm.insert(metal,0);
          metals.addElement(metal);
          
          metal = sprMetal();    //defense for eagle down
          metal.setRefPixelPosition((getWidth()/2)-24,getHeight()-32);
          lm.insert(metal,0);
          metals.addElement(metal);
          
           metal = sprMetal();    //defense for eagle down
          metal.setRefPixelPosition((getWidth()/2)+8,getHeight()-32);
          lm.insert(metal,0);
          metals.addElement(metal);
          
          metal = sprMetal();    //defense for eagle up
          metal.setRefPixelPosition((getWidth()/2)-8, 18);
          lm.insert(metal,0);
          metals.addElement(metal);
          
          metal = sprMetal();    //defense for eagle up
          metal.setRefPixelPosition((getWidth()/2)-24, 18);
          lm.insert(metal,0);
          metals.addElement(metal);
          
          metal = sprMetal();    //defense for eagle up
          metal.setRefPixelPosition((getWidth()/2)+8, 18);
          lm.insert(metal,0);
          metals.addElement(metal);            
        
          boom = sprBoom();
    }    
    public void run() {      	          
        flushGraphics();
        String rm = null;
        //opponent control
        while (!over) {
        	try {        	
       		if(in.available() != 0) {
       			rm=in.readUTF();
       			if(server){
       			moveR(rm.charAt(0),tank1, tank1.getRefPixelX(), tank1.getRefPixelY());       			
       			} else {
       			moveR(rm.charAt(0),tank, tank.getRefPixelX(), tank.getRefPixelY());	
       			}
       			rm = null;
       		}
        	} catch (Exception e) {}
        	
        	//own control
        	if(server) {        
        	moveTank(tank, tank.getRefPixelX(), tank.getRefPixelY());        	
        	} else {       	
        	moveTank(tank1, tank1.getRefPixelX(), tank1.getRefPixelY());        	
        	}   
  	                                         
            //missile update
            for (int i = booms.size() - 1; i >= 0; i--) {
                Sprite bm = (Sprite) booms.elementAt(i);
                bm.nextFrame();
                bm.setRefPixelPosition(bm.getRefPixelX(), bm.getRefPixelY());
                if (bm.getFrame() == 0) {
                    booms.removeElement(bm);                   
                    lm.remove(bm);
                }
            }
            
             for (int i = missiles0.size() - 1; i >= 0; i--) {		
                Sprite ms = (Sprite) missiles0.elementAt(i);                              
                ms.setRefPixelPosition(ms.getRefPixelX(), ms.getRefPixelY() - 4);   // upwards shooting                                                                                              
                // missile reached a screen's end
                if (ms.getRefPixelY() < 0) {                   
                    missiles0.removeElement(ms);                    
                    lm.remove(ms);                
                }                
                checkcollision(missiles0,ms);                
            
             }
            for (int i = missiles1.size() - 1; i >= 0; i--) {		
                Sprite ms = (Sprite) missiles1.elementAt(i);                                              
                ms.setRefPixelPosition(ms.getRefPixelX(), ms.getRefPixelY() + 4);  // downwards shooting                                                                                              
                // missile reached a screen's end
                if (ms.getRefPixelY() > getHeight()) {
                    missiles1.removeElement(ms);
                    lm.remove(ms);                
                }
                checkcollision(missiles1,ms);                
             }
            
            for (int i = missiles2.size() - 1; i >= 0; i--) {	
                Sprite ms = (Sprite) missiles2.elementAt(i);
                ms.setTransform(5);                              
                ms.setRefPixelPosition(ms.getRefPixelX() - 4, ms.getRefPixelY()); // left shooting                                                                                              
                // missile reached a screen's end
                if (ms.getRefPixelX() < 0) {                    
                    missiles2.removeElement(ms);
                    lm.remove(ms);                
                }
                checkcollision(missiles2,ms);             
             }
                for (int i = missiles3.size() - 1; i >= 0; i--) {		
                Sprite ms = (Sprite) missiles3.elementAt(i);   
                 ms.setTransform(5);                           
                 ms.setRefPixelPosition(ms.getRefPixelX() + 4, ms.getRefPixelY());  // right shooting                                                                                                           
                // missile reached a screen's end
                if (ms.getRefPixelX() > getWidth()) {
                    // yes...  remove missile
                    missiles3.removeElement(ms);
                    lm.remove(ms);                
                }
                checkcollision(missiles3,ms);               
             }
            
            g.fillRect(0,0,getWidth(),getHeight());
            lm.paint(g, 0, 0);
          	flushGraphics();
          	try {Thread.sleep(35);} catch(Exception e) {}                 
         }
         g.setColor(WHITE);
         g.setFont(fontLarge);
         if(winner){         	
             g.drawString("W H I T E  T A N K  W I N S", 
                             getWidth() / 2, getHeight() / 2 - flHalf, TOPHCENTER);
         } else {
         	g.drawString("R E D  T A N K  W I N S", 
                             getWidth() / 2, getHeight() / 2 - flHalf, TOPHCENTER);
         }
         lm.paint(g,0,0);
         flushGraphics();
         try{Thread.sleep(1000);} catch(Exception e) {}
         main.startApp();
         
    }
    public void moveR(char key,Sprite tank,int tankX, int tankY) {   	
    	switch(key) {
    		case 'L': tank.setRefPixelPosition(tankX-2, tankY);
                  	tank.setTransform(6);
                  	direction = 2;
                break;
    		case 'R': tank.setRefPixelPosition(tankX+2, tankY);
                    tank.setTransform(5);
                    direction = 3;
    			break;
    		case 'U': tank.setRefPixelPosition(tankX, tankY-2);
                    tank.setTransform(0);
                    direction = 0;
    			break;
    		case 'D': tank.setRefPixelPosition(tankX, tankY+2);
                    tank.setTransform(3);
                    direction = 1;
    			break;
    		case 'F': Sprite m = new Sprite(missile);                                             
                        switch(direction) {                        
                        case 0:
                        	m.setRefPixelPosition(tank.getRefPixelX(), 
                                              tank.getRefPixelY()-8);
                            missiles0.addElement(m); 
                        	break;//up                        			
                        case 1:
                        	m.setRefPixelPosition(tank.getRefPixelX(), 
                                              tank.getRefPixelY()+8);	
                        	missiles1.addElement(m); 
                        	break;//down
                        case 2: 
                        	m.setRefPixelPosition(tank.getRefPixelX()-8, 
                                              tank.getRefPixelY());
                        	missiles2.addElement(m);
                         break;//left
                        case 3: 
                        	m.setRefPixelPosition(tank.getRefPixelX()+8, 
                                              tank.getRefPixelY());
                        	missiles3.addElement(m); 
                        	break;//right
                        }
                 		lm.insert(m, 0);
    			break;
    	}
    }    
    public void moveTank(Sprite tank, int tankX, int tankY) {
    		int keyStates = getKeyStates();    	
    		try {        
            if ((keyStates & LEFT_PRESSED) != 0) {           	            
            	 if(tankX>=7) {             	 	              	      
                    tankX -= 2; 
                    tank.setRefPixelPosition(tankX, tankY);                  
                  	tank.setTransform(6);                  	
                  	dir=2;
                  	if(movecollision(tank)){
                  		tank.setRefPixelPosition(tankX+2, tankY); 	
                  	} else {                
                  	out.writeUTF("L");
					out.flush();
                  	}
                	}                                		               	                    
            } 
            if ((keyStates & RIGHT_PRESSED) != 0) {             	 			
               		if(tankX<=getWidth()-7) {              		                					
                    tankX += 2;
                    tank.setRefPixelPosition(tankX, tankY);
                    tank.setTransform(5);
                    dir=3;
                    if(movecollision(tank)){
                  		tank.setRefPixelPosition(tankX-2, tankY); 	
                  	} else {                
                  	out.writeUTF("R");
					out.flush();
                  	}                   
               		} 	
            }
            if ((keyStates & UP_PRESSED) != 0) {	
               		if(tankY>=7) {                   			    	       
                    tankY -= 2;
                    tank.setRefPixelPosition(tankX, tankY);
                    tank.setTransform(0);
                    dir=0;
                    if(movecollision(tank)){
                  		tank.setRefPixelPosition(tankX, tankY+2); 	
                  	} else {                
                  	out.writeUTF("U");
					out.flush();
                  	}                   
               		}        	          
            } 
            if ((keyStates & DOWN_PRESSED) != 0) {  
                	if(tankY<=getHeight()-7) {               		
                    tankY += 2;
                    tank.setRefPixelPosition(tankX, tankY);
                    tank.setTransform(3);
                    dir=1;
                    if(movecollision(tank)){
                  		tank.setRefPixelPosition(tankX, tankY-2); 	
                  	} else {                
                  	out.writeUTF("D");
					out.flush();
                  	}                    
                	}      	               			                	                          
            }                           
            count++;
            if ((keyStates & FIRE_PRESSED) != 0) {                         
                        if(count > 15) {
                         count=0;                                                                               
                        Sprite m = new Sprite(missile);                                             
                        switch(dir) {                        
                        case 0:
                        	m.setRefPixelPosition(tank.getRefPixelX(), 
                                              tank.getRefPixelY()-8);
                            missiles0.addElement(m); 
                        	break;//up                        			
                        case 1:
                        	m.setRefPixelPosition(tank.getRefPixelX(), 
                                              tank.getRefPixelY()+8);	
                        	missiles1.addElement(m); 
                        	break;//down
                        case 2: 
                        	m.setRefPixelPosition(tank.getRefPixelX()-8, 
                                              tank.getRefPixelY());
                        	missiles2.addElement(m);
                         break;//left
                        case 3: 
                        	m.setRefPixelPosition(tank.getRefPixelX()+8, 
                                              tank.getRefPixelY());
                        	missiles3.addElement(m); 
                        	break;//right
                        }
                 		lm.insert(m, 0);
                 		out.writeUTF("F");
						out.flush();
                        }              
            }
    		}catch (Exception e) {}
    		
    }
    public boolean movecollision(Sprite tank){  
    		if(server){		// tanks
    			Sprite obss = (Sprite)tanks.elementAt(1);
    			if(tank.collidesWith(obss,true)){
    				return true;
    			}
    		} else {
    			Sprite obss = (Sprite)tanks.elementAt(0);
    			if(tank.collidesWith(obss,true)){
    				return true;
    			}
    		}	
    	for(int i=0;i<metals.size();i++){	// metals
    		Sprite obs = (Sprite)metals.elementAt(i);
    		if(tank.collidesWith(obs,true)){
    			return true;
    		}
    	}
    	for(int i=0;i<waters.size();i++){	// waters
    		Sprite obs = (Sprite)waters.elementAt(i);
    		if(tank.collidesWith(obs,true)){
    			return true;
    		}
    	}
    	for(int i=0;i<bricks.size();i++){	// bricks
    		Sprite obs = (Sprite)bricks.elementAt(i);
    		if(tank.collidesWith(obs,true)){
    			return true;
    		}
    	}
    	for(int i=0;i<eagles.size();i++){	// eagles
    		Sprite obs = (Sprite)eagles.elementAt(i);
    		if(tank.collidesWith(obs,true)){
    			return true;
    		}
    	}
    	return false;
    }   
    public void checkcollision(Vector missiles,Sprite ms)  {
    		for(int i=0; i<tanks.size(); i++) { // collision for tanks
    			Sprite obs = (Sprite)tanks.elementAt(i);
    			if(ms.collidesWith(obs,true)) { 
    			    removeAddBoom(missiles,obs,ms);
    			    if(i==1){
    			    	heart1.nextFrame();
    			    	obs.setRefPixelPosition(getWidth()/4, 26);
    			    	lm.insert(obs,0);
    			    	if(heart1.getFrame()==0){    			    		
    			    		winheartflag();
    			    		flag.nextFrame();
    			    		if(flag.getFrame()==3){
    			    			winner = true;
    			    			over = true;
    			    		}
    			    		
    			    	} 
    			    }else {
    			    	heart.nextFrame();
    			    	obs = (Sprite)tanks.elementAt(0);
    			    	obs.setRefPixelPosition((getWidth()/4)*3, getHeight()-26);
    			    	lm.insert(obs,0);
    			    	if(heart.getFrame()==0){   			    		
    			    		winheartflag();
    			    		flag1.nextFrame();
    			    		if(flag1.getFrame()==3){
    			    			winner = false;
    			    			over = true;
    			    		}  			    		
       			    	}
    			    }         							         				                			
                }
    		}
    		for(int i=0; i<bricks.size(); i++) { // collision for bricks
    			Sprite obs = (Sprite)bricks.elementAt(i);
    			if(ms.collidesWith(obs,true)) { 
    			    removeAddBoom(missiles,obs,ms);         							         				                			
                }
    		}
            for(int i=0; i<eagles.size(); i++) {  //collision for eagles  
            	Sprite obs = (Sprite)eagles.elementAt(i);       
                if(ms.collidesWith(obs,true)) {
                	removeAddBoom(missiles,obs,ms);
                	if(i==1){         	                		
                		flag.nextFrame();
                		if(flag.getFrame()==3){
                			winner=true;
                			over=true;
                		}else{
                			heart1.setFrame(0);
    			    		winflag(obs,false);
    			    	}
                	} else { 		     		
                		flag1.nextFrame();
                		if(flag1.getFrame()==3){
                			winner = false;
                			over=true;
                		}else{
                			heart.setFrame(0);
    			    		winflag(obs,true);
    			    	}
                	}                	                	                             
                }  
            }  
            for(int i=0; i<metals.size(); i++) {  //collision for metals  
            	Sprite obs = (Sprite)metals.elementAt(i);       
                if(ms.collidesWith(obs,true)) {
                	missiles.removeElement(ms);                 			     	
            		lm.remove(ms);
            		Sprite bm = new Sprite(boom);
            		bm.setRefPixelPosition(ms.getRefPixelX(), 
                                   ms.getRefPixelY());
           			lm.insert(bm, 0);
           			booms.addElement(bm);				                             
                }  
            }                		
    }
    public void winflag(Sprite obs,boolean which){
    	try{Thread.sleep(1000);}catch(Exception e){}
    	tank.setRefPixelPosition((getWidth()/4)*3, getHeight()-26);
    	tank1.setRefPixelPosition(getWidth()/4, 26);
    	obs = sprEagle();
    	if(which){
    		eagles.setElementAt(obs,0);   		
    		obs.setRefPixelPosition(getWidth()/2,getHeight()-8);
    	} else {
    		eagles.setElementAt(obs,1);
    		obs.setRefPixelPosition(getWidth()/2,10);
    	}
    	lm.insert(obs,0);
    }
    public void winheartflag(){
    	try{Thread.sleep(1000);}catch(Exception e){}
    	tank.setRefPixelPosition((getWidth()/4)*3, getHeight()-26);
    	tank1.setRefPixelPosition(getWidth()/4, 26);
    }
    public void removeAddBoom(Vector missiles, Sprite obs, Sprite ms){
    		missiles.removeElement(ms);                 			     	
            lm.remove(ms);    
            lm.remove(obs);	                                          	                              	
            Sprite bm = new Sprite(boom);
            bm.setRefPixelPosition(obs.getRefPixelX(), 
                                   obs.getRefPixelY());
            lm.insert(bm, 0);
            booms.addElement(bm);
            obs.setRefPixelPosition(getWidth()+100,getHeight()+100);     	
    }
    private Sprite sprTank() {
        Image img = null;
        try { img = Image.createImage("/tank.png"); } catch (Exception e) {}

        Sprite tank = new Sprite(img);
       
        tank.defineReferencePixel(7, 7);

        return tank;
    }
    private Sprite sprTank1() {
        Image img = null;
        try { img = Image.createImage("/tank1.png"); } catch (Exception e) {}

        Sprite tank1 = new Sprite(img);
       
        tank1.defineReferencePixel(7, 7);

        return tank1;
    }
    public Sprite sprMissile() {
        Image img = null;
        try { img = Image.createImage("/missile.png"); } catch (Exception e) {}
       
        Sprite missile = new Sprite(img, 1, 4);
        missile.defineReferencePixel(0, 3);
       
        return missile;
    }
    public Sprite sprBrick() {
        Image img = null;
        try { img = Image.createImage("/brick.png"); } catch (Exception e) {}
       
        Sprite brick = new Sprite(img);
        brick.defineReferencePixel(0, 3);
       
        return brick;
    }
    public Sprite sprMetal() {
        Image img = null;
        try { img = Image.createImage("/metal.png"); } catch (Exception e) {}
       
        Sprite metal = new Sprite(img);
        brick.defineReferencePixel(0, 3);
       
        return metal;
    }
    public Sprite sprForest() {
        Image img = null;
        try { img = Image.createImage("/forest.png"); } catch (Exception e) {}
       
        Sprite forest = new Sprite(img);
        forest.defineReferencePixel(0, 3);
       
        return forest;
    }
    public Sprite sprWater() {
        Image img = null;
        try { img = Image.createImage("/water.png"); } catch (Exception e) {}
       
        Sprite water = new Sprite(img);
        water.defineReferencePixel(0, 3);
       
        return water;
    }
    public Sprite sprBoom() {
        Image img = null;
        try { img = Image.createImage("/explode.png"); } catch (Exception e) {}
       
        Sprite boom = new Sprite(img, 30, 30);
        boom.setFrameSequence(new int[] {2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0});
        boom.defineReferencePixel(15, 8);
       
        return boom;
    }
     public Sprite sprEagle() {
        Image img = null;
        try { img = Image.createImage("/Eagle.png"); } catch (Exception e) {}
       
        Sprite eagle = new Sprite(img);
        eagle.defineReferencePixel(8, 8);
       
        return eagle;
    }
    public Sprite sprHeart() {
        Image img = null;
        try { img = Image.createImage("/hearts.png"); } catch (Exception e) {}
       
        Sprite heart = new Sprite(img,45,14);
        heart.defineReferencePixel(0, 14);
       
        return heart;
    }
    public Sprite sprFlag() {
        Image img = null;
        try { img = Image.createImage("/flagss.png"); } catch (Exception e) {}
       
        Sprite flag = new Sprite(img,48,15);
        flag.defineReferencePixel(48, 15);
       
        return flag;
    }
}
}
	
