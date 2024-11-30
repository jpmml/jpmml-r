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

import java.io.IOException;
import java.util.Map;
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

	@Override
	public void write(RDataOutput output) throws IOException {
		RExp tag = getTag();
		RExp value = getValue();
		RPair attributes = getAttributes();
		RPair next = getNext();

		int flags = SExpTypes.LISTSXP;

		if(attributes != null){
			flags = SerializationUtil.setHasAttributes(flags);
		} // End if

		if(tag != null){
			flags = SerializationUtil.setHasTag(flags);
		}

		output.writeInt(flags);

		if(attributes != null){
			attributes.write(output);
		} // End if

		if(tag != null){
			RString string = (RString)tag;

			RExpWriter writer = output.getWriter();

			Map<Object, Integer> referenceTable = writer.getReferenceTable();

			Integer index = referenceTable.get(string.getValue());
			if(index == null){
				referenceTable.put(string.getValue(), referenceTable.size() + 1);

				output.writeInt(SExpTypes.SYMSXP);

				string.write(output);
			} else

			{
				output.writeInt(SerializationTypes.REFSXP);

				output.writeInt(index);
			}
		}

		value.write(output);

		if(next != null){
			next.write(output);
		} else

		{
			output.writeInt(SerializationTypes.NILVALUESXP);
		}
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