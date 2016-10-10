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

import java.util.List;

import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.jpmml.converter.ContinuousFeature;

public class InteractionFeature extends ContinuousFeature {

	private List<FieldName> names = null;


	public InteractionFeature(FieldName name, DataType dataType, List<FieldName> names){
		super(name, dataType);

		setNames(names);
	}

	public List<FieldName> getNames(){
		return this.names;
	}

	private void setNames(List<FieldName> names){
		this.names = names;
	}
}