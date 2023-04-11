package tdm.tc.msgs;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCItemPursuing extends TCMessageData {
	
	private static final long serialVersionUID = 8866323423491232L;

	public static final IToken MESSAGE_TYPE = Tokens.get("TCItemPursuing");
	
	private UnrealId who;
	
	private UnrealId what;
	
	private double fitness;
	
	public TCItemPursuing(UnrealId who, UnrealId what, double fitness) {
		super(MESSAGE_TYPE);
		this.who = who;
		this.what = what;
		this.fitness = fitness;
	}

	public UnrealId getWho() {
		return who;
	}

	public void setWho(UnrealId who) {
		this.who = who;
	}

	public UnrealId getWhat() {
		return what;
	}

	public void setWhat(UnrealId what) {
		this.what = what;
	}
	
	public double getFitness() {
		return fitness;
	}

	public void setFitness(double fitness) {
		this.fitness = fitness;
	}
	
}
