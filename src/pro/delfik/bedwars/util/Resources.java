package pro.delfik.bedwars.util;

import pro.delfik.bedwars.game.Resource;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * EnumReference по Color.
 * @param <Value> Тип данных, хранимых в мапе
 * @see Resource
*/

public class Resources<Value> extends EnumReference<Resource, Value> {
	
	public Resources() {
		this(null);
	}
	
	public Resources(Supplier<Value> defaultValue) {
		super(Resource.class, defaultValue);
	}
	
	public <New> Resources<New> convert(Function<Value, New> converter) {
		Resources<New> resources = new Resources<>();
		super.convert(resources, converter);
		return resources;
	}
	
}