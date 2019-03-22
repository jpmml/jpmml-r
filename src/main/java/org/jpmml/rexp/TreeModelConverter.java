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

import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.jpmml.converter.Feature;
import org.jpmml.converter.PredicateManager;

abstract
public class TreeModelConverter<R extends RExp> extends ModelConverter<R> {

	private PredicateManager predicateManager = new PredicateManager();


	public TreeModelConverter(R object){
		super(object);
	}

	public Predicate createSimpleSetPredicate(Feature feature, List<?> values){
		PredicateManager predicateManager = getPredicateManager();

		return predicateManager.createSimpleSetPredicate(feature, values);
	}

	public Predicate createSimplePredicate(Feature feature, SimplePredicate.Operator operator, Object value){
		PredicateManager predicateManager = getPredicateManager();

		return predicateManager.createSimplePredicate(feature, operator, value);
	}

	public PredicateManager getPredicateManager(){
		return this.predicateManager;
	}
}