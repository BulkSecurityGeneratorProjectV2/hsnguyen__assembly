/*****************************************************************************
 * Copyright (c) Minh Duc Cao, Monash Uni & UQ, All rights reserved.         *
 *                                                                           *
 * Redistribution and use in source and binary forms, with or without        *
 * modification, are permitted provided that the following conditions        *
 * are met:                                                                  * 
 *                                                                           *
 * 1. Redistributions of source code must retain the above copyright notice, *
 *    this list of conditions and the following disclaimer.                  *
 * 2. Redistributions in binary form must reproduce the above copyright      *
 *    notice, this list of conditions and the following disclaimer in the    *
 *    documentation and/or other materials provided with the distribution.   *
 * 3. Neither the names of the institutions nor the names of the contributors*
 *    may be used to endorse or promote products derived from this software  *
 *    without specific prior written permission.                             *
 *                                                                           *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS   *
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR         *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR        *
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF    *
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING      *
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS        *
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.              *
 ****************************************************************************/

/**************************     REVISION HISTORY    **************************
 * 30 Jan 2015 - Minh Duc Cao: Created                                        
 *  
 ****************************************************************************/
package japsa.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;


/**
 * Actually enclose a map and a list
 * @author minhduc
 *
 */
public class JapsaListMap<K extends Comparable<? super K> ,V>{
	private ArrayList<K> keyList;
	private HashMap<K,V> map; 

	public JapsaListMap(){
		map = new HashMap<K,V>();
		keyList = new ArrayList<K>();		
	}



	/**
	 * Sort the structure key key
	 */
	public void sort(){
		Collections.sort(keyList);
	}

	public V get(K key) {		
		return map.get(key);
	}
	
	public V put(K key, V value){		
		V oldValue = map.put(key, value);
		if (oldValue == null)
			keyList.add(key);
		
		return oldValue;
	}
	
	public V remove(K key){
		V oldValue = map.remove(key);
		if (oldValue != null)
			keyList.remove(key);
		
		return oldValue;
	}
	
	public int size(){
		return keyList.size();
	}
}
