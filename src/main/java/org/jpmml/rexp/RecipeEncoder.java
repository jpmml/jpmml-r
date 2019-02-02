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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.dmg.pmml.DataField;
import org.dmg.pmml.FieldName;
import org.jpmml.converter.Feature;
import org.jpmml.converter.Label;
import org.jpmml.converter.Schema;

public class RecipeEncoder extends TransformerEncoder<RGenericVector> {

	private Map<FieldName, Role> varRoles = Collections.emptyMap();

	private Map<FieldName, Source> varSources = Collections.emptyMap();

	private Map<FieldName, Type> varTypes = Collections.emptyMap();

	private Map<FieldName, Role> termRoles = Collections.emptyMap();

	private Map<FieldName, Source> termSources = Collections.emptyMap();

	private Map<FieldName, Type> termTypes = Collections.emptyMap();


	public RecipeEncoder(RGenericVector recipe){
		super(recipe);

		RGenericVector varInfo = recipe.getGenericElement("var_info");
		RGenericVector termInfo = recipe.getGenericElement("term_info");

		this.varRoles = parseInfo(varInfo, "role", value -> Role.valueOf(value.toUpperCase()));
		this.varSources = parseInfo(varInfo, "source", value -> Source.valueOf(value.toUpperCase()));
		this.varTypes = parseInfo(varInfo, "type", value -> Type.valueOf(value.toUpperCase()));

		this.termRoles = parseInfo(termInfo, "role", value -> Role.valueOf(value.toUpperCase()));
		this.termSources = parseInfo(termInfo, "source", value -> Source.valueOf(value.toUpperCase()));
		this.termTypes = parseInfo(termInfo, "type", value -> Type.valueOf(value.toUpperCase()));
	}

	@Override
	public Schema createSchema(){
		RGenericVector recipe = getObject();

		Label label = getLabel();
		List<? extends Feature> features = getFeatures();

		RGenericVector steps = recipe.getGenericElement("steps");

		List<FieldName> outcomeNames = this.termRoles.entrySet().stream()
			.filter(entry -> (Role.OUTCOME).equals(entry.getValue()))
			.map(entry -> entry.getKey())
			.collect(Collectors.toList());

		if(outcomeNames.size() == 1){
			FieldName outcomeName = outcomeNames.get(0);

			renameDataField(label.getName(), outcomeName);

			label = label.toRenamedLabel(outcomeName);
		} else

		if(outcomeNames.size() >= 2){
			throw new IllegalArgumentException();
		} // End if

		if(steps != null){
			throw new IllegalArgumentException();
		}

		return new Schema(label, features);
	}

	private void renameDataField(FieldName name, FieldName renamedName){
		DataField dataField = removeDataField(name);

		dataField.setName(renamedName);

		addDataField(dataField);
	}

	static
	private <E extends Enum<E>> Map<FieldName, E> parseInfo(RGenericVector info, String name, Function<String, E> function){
		RStringVector variable = info.getStringElement("variable");
		RStringVector value = info.getStringElement(name);

		if(variable.size() != value.size()){
			throw new IllegalArgumentException();
		}

		Map<FieldName, E> result = new LinkedHashMap<>();

		for(int i = 0; i < variable.size(); i++){
			result.put(FieldName.create(variable.getValue(i)), function.apply(value.getValue(i)));
		}

		return result;
	}

	static
	private enum Role {
		OUTCOME,
		PREDICTOR,
		;
	}

	static
	private enum Source {
		ORIGINAL,
		DERIVED,
		;
	}

	static
	private enum Type {
		LOGICAL,
		NOMINAL,
		NUMERIC
		;
	}
}