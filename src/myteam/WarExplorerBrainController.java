package myteam;

import edu.warbot.agents.MovableWarAgent;
import edu.warbot.agents.WarResource;
import edu.warbot.agents.enums.WarAgentType;
import edu.warbot.agents.percepts.WarAgentPercept;
import edu.warbot.brains.WarBrain;
import edu.warbot.brains.brains.WarExplorerBrain;
import edu.warbot.communications.WarMessage;
import java.util.List;
import java.util.Stack;

public abstract class WarExplorerBrainController extends WarExplorerBrain {
	
    boolean dispo = true;
    boolean waitAnswer = false;
    private int timeOut = 5;
    private int timeWaited = 0;
    private int targetId;
    WTask ctask;
    Stack<WTask> ptask = new Stack<>();

    static WTask handleMsgs = new WTask(){ 
            String exec(WarBrain bc){return "";}
    };
    
    static WTask giveFood = new WTask(){ 
        String exec(WarBrain bc){
            WarExplorerBrainController me = (WarExplorerBrainController) bc;
            if(me.timeWaited < me.timeOut){
                if(me.getNbElementsInBag() > 0){
                    me.setDebugString("delivering food");
                    me.sendMessage(me.targetId, WarUtilMessage.WHERE_ARE_YOU, "");
                    List<WarMessage> messages = me.getMessages();
                    boolean messageReceive = false;
                    for(WarMessage message : messages){
                        if(me.targetId == message.getSenderID()){
                            messageReceive = true;
                            if(message.getMessage().equals(WarUtilMessage.IM_HERE)){
                                me.timeWaited = 0;
                                if(message.getDistance() < MovableWarAgent.MAX_DISTANCE_GIVE){
                                    me.setIdNextAgentToGive(me.targetId);
                                    return me.give();
                                }
                                else{
                                    me.setHeading(message.getAngle());
                                }
                            }
                            if(message.getMessage().equals(WarUtilMessage.IM_FINE)){
                                me.dispo = true;
                                me.ctask = me.ptask.pop();
                            }
                        }
                    }
                    if(!messageReceive){
                        me.timeWaited++;
                    }
                }
                else{
                    me.setDebugString("no more food");
                    me.sendMessage(me.targetId, WarUtilMessage.QUIT_ENROLMENT,"");
                    me.ctask = me.ptask.pop();
                    me.dispo = true;
                }
            }
            else{
                me.setDebugString("time out");
                me.sendMessage(me.targetId, WarUtilMessage.QUIT_ENROLMENT,"");
                me.ctask = me.ptask.pop();
                me.dispo = true;
            }
            
            if(me.isBlocked())
                me.setRandomHeading();

            return me.move();
        }
    };

    static WTask returnFoodTask = new WTask(){
        String exec(WarBrain bc){
            WarExplorerBrainController me = (WarExplorerBrainController) bc;
            if(me.isBagEmpty()){
                me.setHeading(me.getHeading() + 180);

                me.ctask = getFoodTask;
                return(null);
            }

            //me.setDebugStringColor(Color.green.darker());
            me.setDebugString("Returning Food");

            List<WarAgentPercept> basePercepts = me.getPerceptsAlliesByType(WarAgentType.WarBase);

            //Si je ne vois pas de base
            if(basePercepts == null | basePercepts.size() == 0){

                WarMessage m = me.getMessageFromBase();
                //Si j'ai un message de la base je vais vers elle
                if(m != null)
                    me.setHeading(m.getAngle());
            }else{//si je vois une base
                WarAgentPercept base = basePercepts.get(0);

                if(base.getDistance() > MovableWarAgent.MAX_DISTANCE_GIVE){
                    me.setHeading(base.getAngle());
                }else{
                    me.setIdNextAgentToGive(base.getID());
                    return me.give();
                }
            }
            
            if(me.isBlocked())
                me.setRandomHeading();
            
            return me.move();
        }
    };

    static WTask getFoodTask = new WTask(){
        String exec(WarBrain bc){
            WarExplorerBrainController me = (WarExplorerBrainController) bc;
            if(me.isBagFull()){
                me.ctask = returnFoodTask;
                return me.idle();
            }

            

            //me.setDebugStringColor(Color.BLACK);
            me.setDebugString("Searching food");

            WarAgentPercept foodPercept = WarUtilAction.getperceptFood(me);

            //Si il y a de la nouriture
            if(foodPercept != null){
                me.broadcastMessageToAll(WarUtilMessage.FOOD_FOUND, "");
                if(foodPercept.getDistance() > WarResource.MAX_DISTANCE_TAKE){
                        me.setHeading(foodPercept.getAngle());
                }else{
                        return me.take();
                }
            }
            else{
                WarMessage m = me.getMessageAboutFood();
                if(m != null){
                    me.setHeading(m.getAngle());
                }
            }
            if(me.isBlocked())
                me.setRandomHeading();
            
            return me.move();
        }
    };



    public WarExplorerBrainController() {
        super();
        ctask = getFoodTask; // initialisation de la FSM
    }

@Override
    public String action() {

        // Develop behaviour here
        String toReturn = this.reflexe();
        if(toReturn != null){
            return toReturn;
        }

        toReturn = ctask.exec(this);   // le run de la FSM

        if(toReturn == null){
            if (isBlocked())
                    setRandomHeading();
            return this.move();
        } else {
            return toReturn;
        }
    }
    
    private String reflexe(){
        if (this.getHealth() < this.getMaxHealth() * 0.5){
            if(this.getNbElementsInBag()>0){
                return ACTION_EAT;
            }
        }
        List<WarMessage> messages = this.getMessages();
        
            
        for(WarMessage message : messages){
            if(dispo){
                this.respondToOffer(message);
            }
            if(waitAnswer){
                this.beHired(message);
            }
        }
        return null;
    }


    private WarMessage getMessageAboutFood() {
        for (WarMessage m : getMessages()) {
            if(m.getMessage().equals(WarUtilMessage.FOOD_FOUND))
                return m;
        }
        return null;
    }

    private WarMessage getMessageFromBase() {
        for (WarMessage m : getMessages()) {
            if(m.getSenderType().equals(WarAgentType.WarBase))
                return m;
        }

        broadcastMessageToAgentType(WarAgentType.WarBase, WarUtilMessage.SEARCHING_BASE, "");
        return null;
    }
    
    private void respondToOffer(WarMessage message){
        if(message.getMessage().equals(WarUtilMessage.NEED_SOMEONE)){
            this.setDebugString("someone needs me");
            if(this.getNbElementsInBag()>0){
                this.reply(message, WarUtilMessage.DISPO, "");
                this.dispo = false;
                this.waitAnswer = true;
            }
        }
    }
    
    private void beHired(WarMessage message){
        this.setDebugString("waiting for answer");
        if(message.getMessage().equals(WarUtilMessage.CHOOSE_YOU)){
            this.waitAnswer=false;
            switch(message.getContent()[0]){
                case WarUtilMessage.NEED_HEALTH :
                    this.ptask.add(this.ctask);
                    this.ctask = giveFood;
                    this.targetId = message.getSenderID();
                    break;
                default :
                    break;
            }
        }
        if(message.getMessage().equals(WarUtilMessage.NOT_CHOOSE_YOU)){
            this.setDebugString("someone refuses me");
            this.dispo=true;
            this.waitAnswer=false;
        }
    }

}