package org.rtassembly.npgraph;

import org.graphstream.graph.implementations.AbstractNode;

public class BidirectedEdgePrototype{
	NodeState n0,n1;

	public BidirectedEdgePrototype(AbstractNode node, boolean dir){
		n0=new NodeState(node, dir);
	}
	public BidirectedEdgePrototype(AbstractNode node1, AbstractNode node2, boolean dir1, boolean dir2){
		n0=new NodeState(node1, dir1);
		n1=new NodeState(node2,dir2);
	}
	public BidirectedEdgePrototype(BidirectedPath path) throws Exception {
		if(path==null || path.size()<=1)
			throw new Exception("Invalid path to make edge prototype!");
		else{
			BidirectedNode 	start=(BidirectedNode) path.getRoot(),
							end=(BidirectedNode) path.peekNode();
			boolean startDir=((BidirectedEdge) path.getEdgePath().get(0)).getDir(start),
					endDir=((BidirectedEdge) path.peekEdge()).getDir(end);
			n0=new NodeState(start,startDir);
			n1=new NodeState(end,endDir);
			}
	}
	public BidirectedEdgePrototype(Alignment from, Alignment to){
		if(from!=null)
			n0=new NodeState(from.node, from.strand);
		if(to!=null)
			n1=new NodeState(to.node, !to.strand);
		
	}
	
	public BidirectedEdgePrototype reverse() {
		return new BidirectedEdgePrototype(n1.getNode(), n0.getNode(), n1.getDir(), n0.getDir());
	}
	
	public AbstractNode getNode0(){
		return n0.getNode();
	}
	public AbstractNode getNode1(){
		return n1.getNode();
	}

	public boolean getDir0(){
		return n0.getDir();
	}
	public boolean getDir1(){
		return n1.getDir();
	}
	
	public int getEndingsNum(){
		int retval=0;
		if(n0!=null) retval++;
		if(n1!=null) retval++;
		
		return retval;
	}
	public String toString(){
		return n0.toString() + "," + n1.toString();
	}
}

class NodeState implements Comparable<NodeState>{
	AbstractNode node;
	boolean dir;
	int weight;//for shortest path finding
	
	NodeState(AbstractNode node, boolean dir){
		this.node=node;
		this.dir=dir;
	}
	NodeState(AbstractNode node, boolean dir, int weight){
		this.node=node;
		this.dir=dir;
		this.weight=weight;
	}
	public AbstractNode getNode(){return node;}
	public boolean getDir(){
		assert node!=null: "Null node doesn't have direction!";
		return dir;
	}
	public int getWeight() {return  weight;}
	public void setWeight(int w) {weight=w;}
	
	public String toString(){
		if(node==null)
			return "-";
		
		return node.getId()+ (dir?"o":"i");
	}

	@Override
	public int compareTo(NodeState o) {
		return Integer.compare(weight, o.weight);
	}
    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final NodeState other = (NodeState) obj;
        if (this.toString()==other.toString())   
        	return true;
        else 
        	return false;
    }
}