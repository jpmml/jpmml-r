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

	public RecipeEncoder(RGenericVector recipe){
		super(recipe);
	}

	@Override
	public Schema transformSchema(Schema schema){
		RGenericVector recipe = getObject();

		RGenericVector varInfo = (RGenericVector)recipe.getValue("var_info");
		RGenericVector termInfo = (RGenericVector)recipe.getValue("term_info");
		RGenericVector steps = (RGenericVector)recipe.getValue("steps");

		Map<FieldName, Role> varRoles = parseInfo(varInfo, "role", value -> Role.valueOf(value.toUpperCase()));
		Map<FieldName, Source> varSources = parseInfo(varInfo, "source", value -> Source.valueOf(value.toUpperCase()));
		Map<FieldName, Type> varTypes = parseInfo(varInfo, "type", value -> Type.valueOf(value.toUpperCase()));

		Map<FieldName, Role> termRoles = parseInfo(termInfo, "role", value -> Role.valueOf(value.toUpperCase()));
		Map<FieldName, Source> termSources = parseInfo(termInfo, "source", value -> Source.valueOf(value.toUpperCase()));
		Map<FieldName, Type> termTypes = parseInfo(termInfo, "type", value -> Type.valueOf(value.toUpperCase()));

		Label label = schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		List<FieldName> outcomeNames = termRoles.entrySet().stream()
			.filter(entry -> (Role.OUTCOME).equals(entry.getValue()))
			.map(entry -> entry.getKey())
			.collect(Collectors.toList());

		if(outcomeNames.size() == 1){
			FieldName outcomeName = outcomeNames.get(0);

			renameDataField(label.getName(), outcomeName);

			label = label.toRenamedLabel(outcomeName);

			schema = new Schema(label, features);
		} else

		if(outcomeNames.size() >= 2){
			throw new IllegalArgumentException();
		} // End if

		if(steps != null){
			throw new IllegalArgumentException();
		}

		return schema;
	}

	private void renameDataField(FieldName name, FieldName renamedName){
		DataField dataField = removeDataField(name);

		dataField.setName(renamedName);

		addDataField(dataField);
	}

	static
	private <E extends Enum<E>> Map<FieldName, E> parseInfo(RGenericVector info, String name, Function<String, E> function){
		RStringVector variable = (RStringVector)info.getValue("variable");
		RStringVector value = (RStringVector)info.getValue(name);

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