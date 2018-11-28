package org.rtassembly.npgraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.graphstream.graph.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//A bridge structure that the first node must be unique
public class GoInBetweenBridge {
	private static final Logger LOG = LoggerFactory.getLogger(GoInBetweenBridge.class);
	
	BDGraph graph; //partial order graph saving possible paths
	PopBin bin;
	BDEdgePrototype pBridge; //note: the ending nodes must be unique, or else omitted
	ArrayList<BridgeSegment> segments;
	BridgeSteps steps;
	
	GoInBetweenBridge(BDGraph graph, PopBin bin){
		this.graph=graph;
//		segments=new ArrayList<>();
//		steps=new BridgeSteps();
		this.bin=bin;
	}
	//This is for SPAdes path reader only. The input path must have at least one end unique.
	//This unique ending node will be use as the first anchor of the bridge
	GoInBetweenBridge (BDGraph graph, BDPath path){
		this(graph,path.getConsensusUniqueBinOfPath());
		if(	path.size()>1) {
			if(SimpleBinner.getBinIfUnique(path.getRoot())!=null) {
				addSegment(new BridgeSegment(path));
			}
			else if(SimpleBinner.getBinIfUnique(path.peekNode())!=null) {
				addSegment(new BridgeSegment(path.reverse()));
			}
			
			try {
				pBridge=new BDEdgePrototype(path.getFirstNode(), path.getFirstNodeDirection());
			} catch (Exception e) {
				System.err.println("Illegal path to construct pBridge: " + path.getId());
				e.printStackTrace();
			}

		}

	}
	
	public GoInBetweenBridge(BDGraph graph, BridgeSteps steps, PopBin b){
		this(graph,b);
		this.steps=steps;
	}
	
	public GoInBetweenBridge(BDGraph graph, AlignedRead bb, PopBin b) {
		this(graph, b);
		steps=new BridgeSteps(bb);
	}

	public int getNumberOfAnchors() {
		int retval=0;
		if(pBridge!=null) {
			if(pBridge.getNode0()!=null) retval++;
			if(pBridge.getNode1()!=null) retval++;
		}
		return retval;
	}
	
	public BDNodeVecState getLastExtendedTip() {
		BDNodeVecState retval= null;
		if(steps!=null) {
			retval=steps.start;
		}
		if(segments!=null && !segments.isEmpty())
			retval=segments.get(segments.size()-1).endNV;
		return retval;
	}
	/*
	 * How complete this bridge is built. Level of completion is as follow:
	 * 0: nothing, 1: one anchor, 2: two anchors determined, 3: bridge connected, 4: bridge completed
	 */
	
	public int getCompletionLevel() {
		int retval=getNumberOfAnchors();
		if(retval==2 && segments!=null && !segments.isEmpty()) {
			boolean isComplete=true, isConnected=true;
			for(BridgeSegment seg:segments) {
				if(!seg.isConnected())
					isConnected=false;;
				if(seg.getNumberOfPaths()!=1)
					isComplete=false;
			}
			if(isConnected)
				retval=3;
			if(isComplete)
				retval=4;
		}

		return retval;
	}

							
	//Merge 2 bridge (must share at least one same unique end-point) together
	//Return true if merging make the bridge reaching new anchor
	byte merge(GoInBetweenBridge qBridge, boolean toConnect) {
		byte retval=0b00; //unchanged state

		if(qBridge==null || qBridge.getCompletionLevel()==0 || getCompletionLevel()==4)
			return retval;
		
		BDNodeState 	sNode0=pBridge.n0,
						sNode1=pBridge.n1,
						qNode0=qBridge.pBridge.n0,
						qNode1=qBridge.pBridge.n1;

		System.out.printf("Merging Bridge %s lvl=%d \n%s\nwith Bridge %s lvl=%d\n%s\n", getEndingsID(), getCompletionLevel(), getAllNodeVector(), qBridge.getEndingsID(), qBridge.getCompletionLevel(), qBridge.getAllNodeVector());
//		System.out.println("sNode0=" + (sNode0==null?"null":sNode0.getId()));
//		System.out.println("sNode1=" + (sNode1==null?"null":sNode1.getId()));
//		System.out.println("qNode0=" + (qNode0==null?"null":qNode0.getId()));
//		System.out.println("qNode1=" + (qNode1==null?"null":qNode1.getId()));

		boolean found=true;
		if(!sNode0.equals(qNode0)) {							//if the starting points don't agree
			if(sNode0.equals(qNode1)) {						//the first unique contig actually located at the end of alignments list
				qBridge.reverse();
			}else if(sNode1!=null){						//if not: this pBridge must have 2 anchors, the first anchor is not in the alignments list
				reverse();							//flip the first and second anchors: now the second anchor is used as shared maker between subject and query bridge
				if(sNode1.equals(qNode1))	
					qBridge.reverse();
				else if(!sNode1.equals(qNode0))
					found=false;
			}else { 
				found=false;
			}
		}
		if(!found) {
//			System.err.println("Not found common end point between merging bridges");
			return retval;
		}
		
		BridgeSteps qSteps=qBridge.steps;
		
		Iterator<BDNodeVecState> iterator = qSteps.nodes.iterator();		
		BDNodeVecState 	current=null;
		int lastIdx=0, numberOfAnchorsBefore=getNumberOfAnchors();
		while(iterator.hasNext()){
			current=iterator.next();
			if(segments!=null){//&& !steps.nodes.contains(current)???
				int prev=-1, cur;
				for(int i=lastIdx; i<segments.size();i++) {
					BridgeSegment curSeg=segments.get(i);
					int numPathsBefore=curSeg.connectedPaths.size();
					cur=curSeg.locateAndVote(current);
					int numPathsAfter=curSeg.connectedPaths.size();
					
					if(numPathsBefore!=1 && numPathsAfter==1)
						retval=0b01;//code representing number of paths reduced to 1					
					
					if(cur<prev)
						break;
					else {
						prev=cur;
						lastIdx=i;
					}
				}
				
			}
			if(getCompletionLevel()<3){
				//just adding
				steps.addNode(current);
			}	
			
		}	
		
		if(toConnect && getCompletionLevel()<3)
			steps.connectBridgeSteps(false);	
		
		if(getNumberOfAnchors()>numberOfAnchorsBefore)
			retval=(byte)(retval+0b10);
		
		return retval;
	}

	//return true if there is change in the number of anchor from the updated bridge

	byte merge(AlignedRead read, boolean toConnect) {
		byte retval=0b00; //unchanged state
		if(read==null || read.getAlignmentRecords().size() < 2 || getCompletionLevel()==4)
			return retval;
		
		BDNodeState 	sNode0=pBridge.n0,
						sNode1=pBridge.n1,
						qNode0=new BDNodeState(read.getFirstAlignment().node, read.getFirstAlignment().strand),
						qNode1=new BDNodeState(read.getLastAlignment().node, !read.getLastAlignment().strand);

		System.out.printf("Merging Bridge %s lvl=%d \n%s\nwith AlignedRead %s\n%s\n", getEndingsID(), getCompletionLevel(), getAllNodeVector(), read.getEndingsID(), read.getCompsString());
//		System.out.println("sNode0=" + (sNode0==null?"null":sNode0.getId()));
//		System.out.println("sNode1=" + (sNode1==null?"null":sNode1.getId()));
//		System.out.println("qNode0=" + (qNode0==null?"null":qNode0.getId()));
//		System.out.println("qNode1=" + (qNode1==null?"null":qNode1.getId()));

		boolean found=true;
		if(!sNode0.equals(qNode0)) {							//if the starting points don't agree
			if(sNode0.equals(qNode1)) {						//the first unique contig actually located at the end of alignments list
				read.reverse();
			}else if(sNode1!=null){						//if not: this pBridge must have 2 anchors, the first anchor is not in the alignments list
				reverse();							//flip the first and second anchors: now the second anchor is used as shared maker between subject and query bridge
				if(sNode1.equals(qNode1))	
					read.reverse();
				else if(!sNode1.equals(qNode0))
					found=false;
			}else { 
				found=false;
			}
		}
		if(!found) {
//			System.err.println("Not found common end point to merge");
			return retval;
		}
		
		Alignment start=read.getFirstAlignment();
		BDNodeVecState 	current=null;
		int lastIdx=0, numOfAnchorsBefore=getNumberOfAnchors();
		for(Alignment alg:read.getAlignmentRecords()) {
			current=new BDNodeVecState(alg.node, read.getVector(start,alg));
			if(segments!=null){ //&& !steps.nodes.contains(current)???
				int prev=-1, cur;
				for(int i=lastIdx; i<segments.size();i++) {
					BridgeSegment curSeg=segments.get(i);
					int numPathsBefore=curSeg.connectedPaths.size();
					cur=curSeg.locateAndVote(current);
					int numPathsAfter=curSeg.connectedPaths.size();
					
					if(numPathsBefore!=1 && numPathsAfter==1)
						retval=0b01;//code representing number of paths reduced to 1
					
					if(cur<prev)
						break;
					else {
						prev=cur;
						lastIdx=i;
					}
				}
				
			}
			
			if(getCompletionLevel()<3){
				//just adding
				steps.addNode(current);
			}
		}
		if(toConnect && getCompletionLevel()<3)
			steps.connectBridgeSteps(false);		
				
		
		System.out.printf("After merging: %s\nstart=%s\nend=%s\n", pBridge.toString(), (steps.start==null?"null":steps.start.toString()), (steps.end==null?"null":steps.end.toString()));
		if(getNumberOfAnchors()>numOfAnchorsBefore)
			retval=(byte)(retval+0b10);
		
		return retval;
	
	}
	
	private void addSegment(BridgeSegment seg) {
		if(segments==null)
			segments=new ArrayList<>();
		segments.add(seg);
	}
	
	private void reverse() {
		assert getNumberOfAnchors()==2:"Could only reverse a determined bridge (2 anchors)";
//		System.out.printf("Reversing the bridge %s:\n%s\n", getEndingsID(), getAllNodeVector());

		pBridge=new BDEdgePrototype(pBridge.getNode1(), pBridge.getNode0(), pBridge.getDir1(), pBridge.getDir0());
		//reverse the segments
		if(segments!=null){
			ArrayList<BridgeSegment> tmp = segments;
			segments = new ArrayList<>();
			if(!tmp.isEmpty()) {
				ScaffoldVector brgVector=tmp.get(tmp.size()-1).getEndVector();
				for(int i=tmp.size()-1; i>=0 ;i--)
					addSegment(tmp.get(i).reverse(brgVector));
			}
		}
		//reverse the nodes list
		steps.reverse();
		
//		System.out.printf("Reversed bridge %s:\n%s\n", getEndingsID(), getAllNodeVector());

	}
	
	public String getEndingsID() {
		if(pBridge==null)
			return "-,-";
		else
			return pBridge.toString();
	}
	

	public String getAllPossiblePaths() {
		if(segments==null)
			return "{none}";
		
		String retval = "{\n";
		for(BridgeSegment seg:segments) {
			retval+=seg.pSegment.toString() + ": ";
			if(seg.getNumberOfPaths()==0)
				retval += "()";
			else
				for(BDPath path:seg.connectedPaths)
					retval+="( "+path.getId()+ " : vote=" + path.getVote() + " deviation=" + path.getDeviation() + " )";
			retval+="\n";
		}
		retval+="}";
		return retval;
	}
	
	public String getAllNodeVector(){
		if(steps==null)
			return "empty list of steps";
		return steps.toString();
	}

	public BDPath getBestPath(Node startFrom, Node endAt) { //the markers must be (transformed) unique
		System.out.println("Finding best path from " + startFrom.getId() + " to " + endAt.getId() + " among: \n" + getAllPossiblePaths());
		BDPath best=null,retval=null;
		boolean found=false;
		for(BridgeSegment seg:segments) {
			//finding the start node
			if(!found){
				if(seg.pSegment.getNode0()!=startFrom){
					continue;
				}
				else
					found=true;
			}

			
			if(seg.getNumberOfPaths()>0){
				if(seg.getNumberOfPaths()>1)
					seg.connectedPaths.sort(Comparator.comparing(BDPath::getVote, Comparator.reverseOrder()).thenComparing(BDPath::getDeviation));	
				
				best=seg.connectedPaths.get(0);
				if(retval==null)
					retval=best;
				else
					retval=retval.join(best);//assuming first path has highest score!!!
			}else
				return null;
			
			//escape if end node is reached
			if(seg.pSegment.getNode1()==endAt)
				break;
		}
		
		if(retval!=null)
			retval.setConsensusUniqueBinOfPath(bin);
		return retval;
	}
	
	public int countPathsBetween(Node startFrom, Node endAt){ //the markers must be (transformed) unique
		int retval=0;
		boolean found=false;
		for(BridgeSegment seg:segments) {
			//finding the start node
			if(!found){
				if(seg.pSegment.getNode0()!=startFrom)
					continue;
				else
					found=true;
			}
			if(!seg.isConnected())
				return 0;
			if(retval!=0)
				retval*=seg.connectedPaths.size();
			else
				retval=seg.connectedPaths.size();
			//escape if end node is reached
			if(seg.pSegment.getNode1()==endAt)
				break;
		}
		return retval;
	}
	
	//TODO: combination of getBestPath() + countPathsBeteen() + path.chopAtAnchors()
	//return new unique path to reduce in a building bridge
	public List<BDPath> scanForNewUniquePaths(){
		if(segments==null || segments.isEmpty())
			return null;
		
		List<BDPath> retval = new ArrayList<>();
		BDPath curPath=null;
		SimpleBinner binner=graph.binner;
		PopBin sbin=null;
		for(BridgeSegment seg:segments){
			if(seg.isConnected() && seg.connectedPaths.size()==1){
				sbin=binner.getBinIfUniqueNow(seg.startNV.getNode());

				if(sbin!=null && sbin.isCloseTo(bin)){
					if(curPath!=null) 
						graph.chopPathAtAnchors(curPath).stream().forEach(p->retval.add(p));
					
					curPath=seg.connectedPaths.get(0);
				}else if(curPath!=null)
					curPath.join(seg.connectedPaths.get(0));

			}else{
				curPath=null;
			}
		}
		
		if(curPath!=null && curPath.getEdgeCount()>0) {
			graph.chopPathAtAnchors(curPath).stream().forEach(p->retval.add(p));
		}
		
		return retval;
	}
	
	//Try to look for an ending unique node of a unidentifiable bridge
	//by using isUniqueNow()
	public boolean scanForAnEnd(boolean force){
		if(steps==null)
			return false;
		Iterator<BDNodeVecState> ite = steps.nodes.descendingIterator();
		while(ite.hasNext()){
			BDNodeVecState tmp=ite.next();
			if(!tmp.qc() && !force)
				continue;
			//TODO: find furthest or closest marker??
			if(tmp==steps.start || tmp==steps.end) //there is no (new) end detected
				return false;
			
			PopBin b=graph.binner.getBinIfUniqueNow(tmp.node);
			if(b!=null && b.isCloseTo(bin)){
				steps.end=tmp;
				return true;
			}
				
		}
		return false;
		
	}
	

	/************************************************************************************************
	 ************************************************************************************************
	 * Class to represent a single segment of the whole bridge.
	 * A bridge consists of >=1 segments.
	 ************************************************************************************************/
	class BridgeSegment{
//		ArrayList<Sequence> nnpReads; // to store nanopore data if needed
		ArrayList<BDPath> connectedPaths;
		BDEdgePrototype pSegment;
//		ScaffoldVector startV, endV; // from bridge anchor (always +) to this segment's end
		BDNodeVecState startNV, endNV;
		int bestElections=0;
		BridgeSegment(){}

		BridgeSegment(Alignment start, Alignment end, AlignedRead read){
			pSegment=new BDEdgePrototype(start.node, end.node, start.strand, !end.strand);
			//invoke findPath()?
			startNV=new BDNodeVecState(start.node, read.getVector(read.getFirstAlignment(), start));
			endNV=new BDNodeVecState(start.node, read.getVector(read.getFirstAlignment(), end));
			connectedPaths = graph.DFSAllPaths(start, end);
			
//			if(connectedPaths==null || connectedPaths.isEmpty())
//				connectedPaths = graph.getClosestPaths(start, end);
			
		}
		
		BridgeSegment(BDNodeVecState nv1, BDNodeVecState nv2, boolean dir1, boolean dir2){
			assert Math.abs(nv1.getVector().getMagnitute()) < Math.abs(nv2.getVector().magnitude): "Illegal order of node position!";
			pSegment = new BDEdgePrototype(nv1.getNode(),nv2.getNode(),dir1,dir2);
			startNV = nv1; endNV = nv2;
			int d = ScaffoldVector.composition(nv2.getVector(), ScaffoldVector.reverse(nv1.getVector())).distance(nv1.getNode(), nv2.getNode());
			connectedPaths = graph.DFSAllPaths((BDNode)nv1.getNode(), (BDNode)nv2.getNode(), dir1, dir2, d, false);
			
//			if(connectedPaths==null || connectedPaths.isEmpty())
//				connectedPaths = graph.getClosestPaths((BDNode)node1, dir1, (BDNode)node2, dir2, d, false);
		}
		
		BridgeSegment(BDPath path){
			try {
				pSegment=new BDEdgePrototype(path);
				connectedPaths=new ArrayList<>();
				connectedPaths.add(path);
				
				//only if path cover the whole bridge (1-segment bridge)
				int dist=(int) (path.getLength()
						-(pSegment.getDir0()?0:pSegment.getNode0().getNumber("len"))
						-(pSegment.getDir1()?0:pSegment.getNode1().getNumber("len")));
				
				startNV=new BDNodeVecState(pSegment.getNode0(),new ScaffoldVector(0,1));
				endNV=new BDNodeVecState(pSegment.getNode1(), new ScaffoldVector(pSegment.getDir0()?dist:-dist, pSegment.getDir0()!=pSegment.getDir1()?1:-1));
			} catch (Exception e) {
				LOG.error("Cannot make bridge from this path!");
				e.printStackTrace();
			} 

		}
		
		BridgeSegment reverse(ScaffoldVector brgVector) {
			BridgeSegment retval = new BridgeSegment();
			retval.pSegment=pSegment.reverse();
			retval.startNV=new BDNodeVecState(pSegment.getNode0(), ScaffoldVector.composition(getEndVector(), ScaffoldVector.reverse(brgVector)));
			retval.endNV=new BDNodeVecState(pSegment.getNode1(), ScaffoldVector.composition(getStartVector(), ScaffoldVector.reverse(brgVector)));
			retval.connectedPaths=new ArrayList<>();
			for(BDPath p:connectedPaths)
				retval.connectedPaths.add(p.reverse());
			return retval;
		}
		
		/*
		 * Find a node vector if it appear in one of the paths
		 * Return retval=-1 if not located within the range of this segment
		 * Return retval>0 if found in x paths
		 * Return retval=0 if located in the interval of this segment but not found in any path 
		 */
		int locateAndVote(BDNodeVecState nv) {
			int retval=-1;
			if(!isConnected())
				return retval;
			
			List<BDPath> tobeRemoved=new ArrayList<>();
			if(nv.compareTo(startNV)*nv.compareTo(endNV)<=0) {
				retval=0;
				ScaffoldVector start2nv=ScaffoldVector.composition(nv.getVector(), ScaffoldVector.reverse(getStartVector()));
				int d=start2nv.distance((BDNode) pSegment.getNode0(), nv.getNode());
				for(BDPath p:connectedPaths) {
					if(p.checkDistanceConsistency(pSegment.getNode0(), nv.getNode(), start2nv.direction>0, d) >= 0) {
						p.upVote(1);
						if(p.getVote() > bestElections)
							bestElections=p.getVote();
						retval++;
					}
					else{
						p.downVote(1);
						if(p.getVote() < bestElections-BDGraph.MAX_DIFF)
							tobeRemoved.add(p);
					}
				}
			}
			if(retval>0 && retval<connectedPaths.size())
				connectedPaths.removeAll(tobeRemoved);

			
			return retval;
		}
		int getNumberOfPaths(){
			if(connectedPaths==null)
				return 0;
			else 
				return connectedPaths.size();
		}
		boolean isConnected(){
			return getNumberOfPaths()>=1;
		}
		boolean isUnique(){
			return getNumberOfPaths()==1;
		}
		ScaffoldVector getEndVector() {
			return endNV.getVector();
		}
		ScaffoldVector getStartVector() {
			return startNV.getVector();
		}
		String getId() {
			return pSegment.toString();
		}
	}
	
	/*
	 * Class representing list of found nodes and their position (depicted as vectors)
	 * in-between two ends of a bridge. 
	 * Used for determine segments 
	 */
	class BridgeSteps{
		TreeSet<BDNodeVecState> nodes;
		//save the two unique nodes at 2 endings of the bridge. When end is covered more than threshold, pBridge is set
		BDNodeVecState start, end; 
		
		BridgeSteps(){
			nodes=new TreeSet<BDNodeVecState>();
		}
		
		BridgeSteps(AlignedRead read){
			this();
			if(read.getAlignmentRecords().size()<2)
				return;
			if(SimpleBinner.getBinIfUnique(read.getFirstAlignment().node)==null)
				if(SimpleBinner.getBinIfUnique(read.getLastAlignment().node)!=null)
					read.reverse();
				else
					return;
					
					
			Alignment 	firstAlg = read.getFirstAlignment(),
						curAlg = null;				
					
			start=new BDNodeVecState(firstAlg.node, new ScaffoldVector());
			nodes.add(start);
				
			for(int i=1;i<read.getAlignmentRecords().size();i++) {
				curAlg = read.getAlignmentRecords().get(i);
				BDNodeVecState tmp=new BDNodeVecState(curAlg.node, read.getVector(firstAlg, curAlg));
				nodes.add(tmp);
				
				if(SimpleBinner.getBinIfUnique(curAlg.node)!=null)
					end=tmp;

			}
			//update the pBridge accordingly
			if(end!=null && end.qc()) {
				pBridge=new BDEdgePrototype(firstAlg.node, end.getNode(), firstAlg.strand, end.getDirection(firstAlg.strand));//getDirection doesn't work with self-vector
			}else if(pBridge==null)
				pBridge=new BDEdgePrototype(firstAlg.node, firstAlg.strand);
		}
		
	
		
		void addNode(BDNodeVecState nv) {
			//TODO: optimize it! hashmap??? not linear searching like this!
			//FIXME: acting weird, not call equals() properly for 141o,20o: Because TreeSet used compareTo() instead of equals()!!! 
			//(https://dzone.com/articles/the-hidden-contract-between-equals-and-comparable)
			
			Iterator<BDNodeVecState> ite = nodes.iterator();
			boolean found=false;
			while(ite.hasNext()){
				BDNodeVecState tmp=ite.next();
				if(tmp.merge(nv)){
					//re-assign end node if there is another unique node with higher score
					if(SimpleBinner.getBinIfUnique(tmp.getNode())!=null && !tmp.getVector().isIdentity()) {
						if(!end.equals(tmp)){
							if(end.nodeCover < tmp.nodeCover)
								end=tmp;
							else
								System.out.println("Conflict detected on end node: " + tmp.toString() + " to " + end.toString());
						}
						
					}
					//nv is already in the set
					found=true;
					break;

				}
			}
			
			if(!found) {
				nodes.add(nv);
				if(SimpleBinner.getBinIfUnique(nv.getNode())!=null) {
					if(nv.getVector().isIdentity()){
						start=nv;	
					}else if(end==null){
						end=nv;
					}
				}
			}

			
//			if(nodes.contains(nv)){
//				System.out.println("Found " + nv);
//				Iterator<BDNodeVecState> ite = nodes.iterator();
//				while(ite.hasNext()){
//					BDNodeVecState tmp=ite.next();
//					if(tmp.equals(nv)){
//						tmp.score+=nv.score;
//						//assign end node
//						if(SimpleBinner.getUniqueBin(tmp.getNode())!=null && !tmp.getVector().isIdentity()) {
//							if(end==null)
//								end=tmp;
//							else if(!end.equals(tmp)){
//								if(end.score < tmp.score)
//									end=tmp;
//								else
//									System.out.println("Conflict detected on end node: " + tmp.toString() + " to " + end.toString());
//							}
//							
//						}
//						break;
//
//					}
//				}
//				
//			}else {
//				System.out.println("Not Found " + nv);
//				//assign start node
//				if(SimpleBinner.getUniqueBin(nv.getNode())!=null && nv.getVector().isIdentity())
//					start=nv;
//				nodes.add(nv);
//			}
		}
			
		
		//Try to make the continuous segments (those that have paths connected 2 ends). Return true if it possible
		//starting from a marker (unique or transformed unique node)
		boolean connectBridgeSteps(boolean force){
			if(isIdentifiable()){
				if(connectable() || force){
//					pBridge.n1=new BDNodeState(end.node, end.getDirection(pBridge.getDir0()));
				}else{
					System.err.printf("Bridge %s is not qualified to connect yet: start=%s end=%s\n", pBridge.toString(), (start==null?"null":start.toString()), (end==null?"null":end.toString()));
					return false;
				}

			}else{
				System.err.printf("Bridge %s is not complete to connect!\n", pBridge.toString());
				return false;
			}	
			
			BDNodeVecState startFrom= getLastExtendedTip(),
							endAt=end;

			
			System.out.print("Trying to connect bridge " + pBridge.toString() + " from "+ startFrom.getNode().getId() + " to " + endAt.getNode().getId());
			if(startFrom==endAt) {
				System.out.println(": ignored!");
				return false;
			}else {
				System.out.println("\n"+getAllNodeVector());
			}
			//First build shortest tree from the end
			//TODO: optimize finding path by using this list
			int distance=ScaffoldVector.composition(endAt.getVector(), ScaffoldVector.reverse(startFrom.getVector())).distance(startFrom.getNode(), endAt.getNode());
			HashMap<String,Integer> shortestMapFromEnd = graph.getShortestTreeFromNode(	endAt.getNode(), 
																						endAt.getDirection(pBridge.getDir0()), 
																						distance);

			
			String key=startFrom.getNode().getId()+(pBridge.getDir0()?"i":"o");
			if(startFrom.getNode()!=pBridge.getNode0())
				key=startFrom.getNode().getId()+(startFrom.getDirection(pBridge.getDir0())?"o":"i");
			
			if(!shortestMapFromEnd.containsKey(key)){ //note the trick: direction BEFORE the path started
				System.out.printf("Shortest tree couldn't reach to the other end: ");

				if(!force){
//					pBridge.n1=null;	
					nodes.remove(endAt);
					System.out.println(" ignored!");
					return false;
				}else{
					System.out.println(" proceed for the last attempt (or just to see how it went wrong)!");

				}
			}else{
				System.out.printf("Shortest tree contain the other end: %s=%d\n", key, shortestMapFromEnd.get(key));
			}
			
			Iterator<BDNodeVecState> iterator = nodes.iterator();
			
			BDNodeVecState 	prev=null, 
							current=null;
			boolean retval = false;
			ArrayList<BDNodeVecState> inbetween = new ArrayList<>();
			while(iterator.hasNext()){
				current=iterator.next();
				if(prev==null){
					if(current==startFrom)
						prev=current;
					continue;
				}
				key=current.getNode().getId() + (current.getDirection(pBridge.getDir0())?"o":"i");
				//need a quality-checking here before including into a segment step
				if(	(current==endAt || shortestMapFromEnd.containsKey(current.getNode().getId() + (current.getDirection(pBridge.getDir0())?"o":"i")) )
					&& graph.binner.checkIfBinContainingNode(bin, current.getNode())
					){			 
					if(current.qc() || current==endAt){
						BridgeSegment seg = null;
						if(prev.getVector().isIdentity())
							seg=new BridgeSegment(	prev, current, 
													pBridge.getDir0(), 
													current.getDirection(pBridge.getDir0()));
						else
							seg=new BridgeSegment(	prev, current, 
									!prev.getDirection(pBridge.getDir0()), 
									current.getDirection(pBridge.getDir0()));
						
						System.out.print("...connecting " + seg.getId());
						if(seg.isConnected()){
							System.out.println(" :success!");		
							prev=current;
							//use qc-failed nodes to vote
							if(seg.getNumberOfPaths()>1)
								for(BDNodeVecState nv:inbetween)
									seg.locateAndVote(nv);
							addSegment(seg);
							if(current.equals(endAt)) {
								retval=true;	
								break;
							}
							inbetween=new ArrayList<>();
							
						}else {
							System.out.println(" :skip!");
						}
					}else
						inbetween.add(current);
				}
					
			}
			
			if(!retval) {		
				segments=null;
				System.out.println("Failed to connect + " + pBridge.toString());
				
			}
			//set pBridge end iff endAt node is original unique node
			if(SimpleBinner.getBinIfUnique(endAt.node)!=null)
				pBridge.n1=new BDNodeState(endAt.node, endAt.getDirection(pBridge.getDir0()));
				
			return retval;
		}
		
		boolean isIdentifiable(){
			return start!=null && end!=null;
		}
		
		boolean connectable(){
			if(!isIdentifiable())
				return false;
			
			return start.qc() && end.qc();
		}
		void reverse() {
			//reverse the nodes list
			TreeSet<BDNodeVecState> reversedSet = new TreeSet<BDNodeVecState>();
			ScaffoldVector rev=ScaffoldVector.reverse(end.getVector());//anchors number = 2 so there exist end node
			BDNodeVecState tmp = null;
			for(BDNodeVecState nv:nodes) {
				tmp=new BDNodeVecState(nv.getNode(), ScaffoldVector.composition(nv.getVector(), rev), nv.nodeCover);
				reversedSet.add(tmp);

			}
			nodes=reversedSet;
			//re-assign start and end
			tmp=start;
			start=end;
			end=tmp;
			//change the vector also
			start.setVector(new ScaffoldVector());
			end.setVector(rev);
		}
		
		public String toString(){
			String retval="";
			for(BDNodeVecState nv:nodes)
				retval+=nv.toString() + "\n";
			return retval;
		}
	}





}
