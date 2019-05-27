package org.rtassembly.npgraph;

import java.util.ArrayList;
import java.util.Objects;
import java.util.TreeSet;

/*
 * Class represent a node together with its vector in relative to another root node.
 * Note: the direction here refers to the one of a ScaffoldVector, not bidirected-graph direction (in/out), neither sequence sense/antisense (+/-)
 * although we can translate into appropriate info
 */
public class BDNodeVecState implements Comparable<BDNodeVecState>{
	BDNode node;
	ScaffoldVector vector;
	int nvsScore=Alignment.MIN_QUAL; //alignment score + number of occurences?
	
	public BDNodeVecState(BDNode node, ScaffoldVector vector){
		this.node=node;
		this.vector=new ScaffoldVector(vector.getMagnitute(), vector.getDirection());
	}
	
	public BDNodeVecState(BDNode node, int qual, ScaffoldVector vector){
		this(node, vector);
		nvsScore=qual;
	}
	public BDNodeVecState(Alignment alg, ScaffoldVector vector) {
		this(alg.node, alg.quality, vector);
	}
	//copy constructor
	public BDNodeVecState(BDNodeVecState bdNodeVecState) {
		this(bdNodeVecState.getNode(), bdNodeVecState.getScore(), bdNodeVecState.getVector());
	}

	public BDNode getNode(){return node;}
	public ScaffoldVector getVector(){return vector;}
	
	public void setNode(BDNode node){this.node=node;}
	public void setVector(ScaffoldVector vector){this.vector=vector;}
	
	
	//get the direction of a node based on the root direction (not apply for the root itself!!!)
	public boolean getDirection(boolean rootDir){
		return rootDir!=(vector.getDirection()>0); //XOR: in-out not +/-
	}
	public int getScore(){
		return nvsScore;
	}
	public void setScore(int score){
		nvsScore=score;
	}
	

	@Override
	public String toString(){
		return node.getId() + ":" + vector.toString() + ":" + nvsScore;
	}
	
	//compare in term of distance to the root node: for sortedset
	@Override
	public int compareTo(BDNodeVecState o) {
		if(equals(o))
			return 0;
		else
			return Integer.compare(this.getDistance(), o.getDistance());
	}
    @Override
    public int hashCode() {
    	String tmp=node.getId() + Math.signum(vector.direction);
        return tmp.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true; 

        if (obj == null || obj.getClass() != this.getClass()) { 
        	return false; 
    	}

        final BDNodeVecState other = (BDNodeVecState) obj;
        
        BDNode 	thisNode=this.getNode(),
				thatNode=other.getNode();
        
        if(!Objects.equals(thisNode, thatNode))
        	return false;
        else
        	return (Math.abs(this.getDistance() - other.getDistance()) < thisNode.getNumber("len") - BDGraph.getKmerSize());       	         
    }
    
    public boolean approximate(final Object obj) {
        if (this == obj)
            return true; 

        if (obj == null || obj.getClass() != this.getClass()) { 
        	return false; 
    	}

        final BDNodeVecState other = (BDNodeVecState) obj;
        
        BDNode 	thisNode=this.getNode(),
				thatNode=other.getNode();
        
        if(!Objects.equals(thisNode, thatNode)){
        	return false;
        }else{ 
        	boolean retval;
        	if(SimpleBinner.getBinIfUniqueNow(thatNode)!=null) {
        		retval= (Math.abs(this.getDistance() - other.getDistance()) < thisNode.getNumber("len") - BDGraph.getKmerSize());
        	}
        	else {
        		retval=this.getVector().consistentWith(other.getVector());
        	}
    		return retval;

        }     

    }
    
	public boolean qc() {
		return nvsScore >= BDGraph.SAFE_COUNTS*Alignment.GOOD_QUAL; //60*2
	}

	//Merging 2 equal NodeVectors
	public boolean merge(BDNodeVecState nv){
		if(!this.approximate(nv))
			return false;

		ScaffoldVector 	thisVector=this.getVector(),
						thatVector=nv.getVector();
		//update the vector
		if(!thisVector.isIdentity()){
			thisVector.setMagnitute( (int)((thisVector.getMagnitute()*this.nvsScore+thatVector.getMagnitute()*nv.nvsScore)/(this.nvsScore+nv.nvsScore)));
		}
		
		//update coverage
		this.nvsScore+=nv.nvsScore;
		return true;
	}
	
	/*
	 * Needleman-Wunsch algorithm to return consensus steps
	 * by aligning two step-lists with identical first element
	 */
	public static void NWAlignment(TreeSet<BDNodeVecState> s0, TreeSet<BDNodeVecState> s1){
		assert s0.first().equals(s1.first()):"First alignment must agree!";

		TreeSet<BDNodeVecState> omittedNodes=new TreeSet<>();
		
		BDNodeVecState[] 	as0=s0.toArray( new BDNodeVecState[s0.size()]),
							as1=s1.toArray( new BDNodeVecState[s1.size()]);
		int[][] scoreTab = new int[as0.length][as1.length];
		char[][] moveTab = new char[as0.length][as1.length];
		for(int i=0; i<as0.length; i++){
			scoreTab[i][0] = -as0[i].getDistance();
			moveTab[i][0] = '|';
		}
		for(int i=0; i<as1.length; i++){
			scoreTab[0][i] = -as1[i].getDistance();
			moveTab[0][i] = '_';
		}
		int match, delete, insert;
		for(int i=1; i<as0.length; i++){
			for(int j=1; j<as1.length; j++){
				match=(as0[i].approximate(as1[j])
						?scoreTab[i-1][j-1] + (int)as0[i].getNode().getNumber("len")-Math.abs(as0[i].getDistance()-as1[j].getDistance())
						:Integer.MIN_VALUE);
				delete=scoreTab[i-1][j] - as0[i].getDistance() + as0[i-1].getDistance();
				insert=scoreTab[i][j-1] -as1[j].getDistance() +as1[j-1].getDistance();
				scoreTab[i][j]= Math.max(delete>insert?delete:insert, match);
				if(scoreTab[i][j]==match)
					moveTab[i][j]='\\';
				else if(scoreTab[i][j]==insert)
					moveTab[i][j]='_';
				else if(scoreTab[i][j]==delete)
					moveTab[i][j]='|';

			}
		}
		
		
		int i=as0.length-1, j=as1.length-1;
		ArrayList<Integer> 	m0 = new ArrayList<>(),
							m1 = new ArrayList<>();

		while(i>=0 && j>=0){
			switch(moveTab[i][j]){
				case '\\':
					m0.add(0, i--);
					m1.add(0, j--);
					break;
				case '_':
					j--;
					break;
				case '|':
					i--;
					break;
			}
		}

		
		BDNodeVecState tmp=null, prevOrigNVS=as0[0]; 
		double scale0=1.0, scale1=1.0;

		as0[0].merge(as1[0]);
		//we always has a match at (0,0)
		i=j=0;
		for(int idx=0;idx<m0.size();idx++){
			int ii=m0.get(idx), jj=m1.get(idx);

			int origStep=as0[ii].getDistance()-prevOrigNVS.getDistance();
			as0[ii].merge(as1[jj]);
			
			try{
				scale0=(as0[ii].getDistance()-as0[i].getDistance())*1.0/origStep;
				scale1=(as0[ii].getDistance()-as0[i].getDistance())*1.0/(as1[jj].getDistance()-as1[j].getDistance());
			}catch(ArithmeticException ae){
				System.err.println("ArithmeticException occured! Use previous scale instead...");
			}
			//calibrate the vectors from first list's in-between
			for(int i0=i+1;i0<ii;i0++){
				as0[i0].getVector().setMagnitute((int) (as0[i].getVector().getMagnitute() + 
										(as0[i0].getVector().getMagnitute() - prevOrigNVS.getVector().getMagnitute())*scale0));
			}
			//add vectors from second in-between for later use
			for(int j0=j+1;j0<jj;j0++){
				tmp = new BDNodeVecState(as1[j0]);
				tmp.vector.setMagnitute((int) (as0[i].getVector().getMagnitute() + 
										(as1[j0].getVector().getMagnitute() - as1[j].getVector().getMagnitute())*scale1));
				omittedNodes.add(tmp);
			}
			
			//move to next match coordinate
			prevOrigNVS=new BDNodeVecState(as0[ii]);
			i=ii; j=jj;
		}
		//nodes after the last match...scale=1.0
		for(int i0=i+1;i0<as0.length;i0++)
			as0[i0].vector.setMagnitute((int) (as0[i].getVector().getMagnitute() + 
					(as0[i0].getVector().getMagnitute() - prevOrigNVS.getVector().getMagnitute())*1.0));	
		for(int j0=j+1;j0<as1.length;j0++){
			tmp = new BDNodeVecState(as1[j0]);
			tmp.vector.setMagnitute((int) (as0[i].getVector().getMagnitute() + 
									(as1[j0].getVector().getMagnitute() - as1[j].getVector().getMagnitute())*1.0));
			omittedNodes.add(tmp);
		}
		
		//add all new nodes
		s0.addAll(omittedNodes);

	}

	//get absolute value of the relative distance (can be negative)
	public int getDistance(){
		return Math.abs(vector.relDistance(node));
	}
}
