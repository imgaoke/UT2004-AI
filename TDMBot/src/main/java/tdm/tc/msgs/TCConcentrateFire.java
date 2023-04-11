package tdm.tc.msgs;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCConcentrateFire extends TCMessageData {
	
	private static final long serialVersionUID = 8866323423491233L;

	public static final IToken MESSAGE_TYPE = Tokens.get("TCConcentrateFire");
	
	
	private UnrealId target;
	
	
	public TCConcentrateFire(UnrealId target) {
		super(MESSAGE_TYPE);
		this.target = target;
	}

	public UnrealId getTarget() {
		return target;
	}

	public void setTarget(UnrealId target) {
		this.target = target;
	}
	
}
