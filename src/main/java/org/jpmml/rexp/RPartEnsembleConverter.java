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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.Schema;

abstract
public class RPartEnsembleConverter<R extends RExp> extends ModelConverter<R> {

	private Map<RGenericVector, RPartConverter> converters = new HashMap<>();

	private List<Schema> schemas = new ArrayList<>();


	public RPartEnsembleConverter(R object){
		super(object);
	}

	public RPartConverter createConverter(RGenericVector rpart){
		return new RPartConverter(rpart);
	}

	public void encodeTreeSchemas(RGenericVector trees, RExpEncoder encoder){
		this.schemas.clear();

		for(int i = 0; i < trees.size(); i++){
			RGenericVector tree = (RGenericVector)trees.getValue(i);

			RExpEncoder treeEncoder = new RExpEncoder();

			RPartConverter converter = createConverter(tree);

			converter.encodeSchema(treeEncoder);

			this.converters.put(tree, converter);

			encoder.addFields(treeEncoder);

			Schema schema = treeEncoder.createSchema();

			this.schemas.add(schema);
		}
	}

	public List<TreeModel> encodeTreeModels(RGenericVector trees){
		List<TreeModel> result = new ArrayList<>();

		if(trees.size() != this.schemas.size()){
			throw new IllegalArgumentException();
		}

		for(int i = 0; i < trees.size(); i++){
			RGenericVector tree = (RGenericVector)trees.getValue(i);
			Schema schema = this.schemas.get(i);

			RPartConverter converter = this.converters.get(tree);
			if(converter == null){
				throw new IllegalArgumentException();
			}

			Schema segmentSchema = schema.toAnonymousSchema();

			TreeModel treeModel = converter.encodeModel(segmentSchema);

			result.add(treeModel);
		}

		return result;
	}
}