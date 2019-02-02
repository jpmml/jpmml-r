/*
 * Copyright (c) 2018 Villu Ruusmann
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

import java.util.ArrayList;
import java.util.List;

import org.dmg.pmml.DataField;
import org.dmg.pmml.FieldName;
import org.jpmml.converter.Feature;

public class SchemaUtil {

	private SchemaUtil(){
	}

	static
	public void setLabel(Formula formula, RExp terms, RExp levels, RExpEncoder encoder){
		RIntegerVector response = terms.getIntegerAttribute("response");

		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			DataField dataField = (DataField)formula.getField(responseIndex - 1);

			FieldName name = dataField.getName();

			if(encoder.getDataField(name) == null){
				encoder.addDataField(dataField);
			} // End if

			if(levels instanceof RStringVector){
				RStringVector stringLevels = (RStringVector)levels;

				dataField = (DataField)encoder.toCategorical(name, stringLevels.getValues());
			} else

			if(levels instanceof RIntegerVector){
				RIntegerVector factorLevels = (RIntegerVector)levels;

				if(!factorLevels.isFactor()){
					throw new IllegalArgumentException();
				}

				dataField = (DataField)encoder.toCategorical(name, factorLevels.getLevelValues());
			} else

			if(levels != null){
				throw new IllegalArgumentException();
			}

			encoder.setLabel(dataField);
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	static
	public void addFeatures(Formula formula, RStringVector names, boolean allowInteractions, RExpEncoder encoder){
		addFeatures(formula, names.getValues(), allowInteractions, encoder);
	}

	static
	public void addFeatures(Formula formula, List<String> names, boolean allowInteractions, RExpEncoder encoder){

		for(int i = 0; i < names.size(); i++){
			String name = names.get(i);

			Feature feature;

			if(allowInteractions){
				feature = formula.resolveFeature(name);
			} else

			{
				feature = formula.resolveFeature(FieldName.create(name));
			}

			encoder.addFeature(feature);
		}
	}

	static
	public List<String> removeSpecialSymbol(List<String> names, String specialName){
		int index = names.indexOf(specialName);

		if(index > -1){
			names = new ArrayList<>(names);

			names.remove(index);
		}

		return names;
	}

	static
	public List<String> removeSpecialSymbol(List<String> names, String specialName, int specialNameIndex){
		String name = names.get(specialNameIndex);

		if((name).equals(specialName)){
			names = new ArrayList<>(names);

			names.remove(specialNameIndex);
		} else

		{
			throw new IllegalArgumentException();
		}

		return names;
	}
}