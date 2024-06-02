/*
 * Copyright (c) 2016 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp;

import java.util.Objects;

public class RPair extends RExp {

	private RExp tag = null;

	private RExp value = null;

	private RPair next = null;


	public RPair(RExp tag, RExp value, RPair attributes){
		super(attributes);

		setTag(tag);
		setValue(value);
	}

	public RPair getValue(int index){
		RPair result = this;

		for(int i = 0; i < index; i++){

			if(result == null){
				throw new IllegalArgumentException();
			} // End if

			if(i == index){
				break;
			}

			result = result.getNext();
		}

		return result;
	}

	public boolean tagEquals(String string){
		RString tag = (RString)getTag();

		return Objects.equals(string, tag != null ? tag.getValue() : null);
	}

	public RExp getTag(){
		return this.tag;
	}

	void setTag(RExp tag){
		this.tag = tag;
	}

	public RExp getValue(){
		return this.value;
	}

	void setValue(RExp value){
		this.value = value;
	}

	public RPair getNext(){
		return this.next;
	}

	void setNext(RPair next){
		this.next = next;
	}
}