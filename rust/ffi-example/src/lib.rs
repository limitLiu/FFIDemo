use jni::{
  objects::{JClass, JFloatArray, JString},
  sys::{jfloatArray, jint, jstring},
  JNIEnv,
};
use md5::compute;

#[no_mangle]
pub extern "system" fn Java_rust_ffi_Demo_md5(mut env: JNIEnv, _: JClass, buf: JString) -> jstring {
  let input: String = match env.get_string(&buf) {
    Ok(x) => x.into(),
    Err(err) => {
      env.exception_clear().expect("clear");
      env
        .throw_new("java/lang/Exception", format!("{err:?}"))
        .expect("throw");
      return std::ptr::null_mut();
    }
  };
  let output = env
    .new_string(format!("{:x}", compute(input)))
    .expect("Failed to new string");
  output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_rust_ffi_Demo_transform(
  env: JNIEnv,
  _: JClass,
  array: JFloatArray,
) -> jfloatArray {
  let len = env.get_array_length(&array).unwrap_or(0);
  let mut v = vec![0f32; len as usize];
  env
    .get_float_array_region(array, 0, &mut v)
    .expect("Failed to copy array to vec");

  let v: Vec<f32> = v.into_iter().map(|x| x * 2.0).collect();
  let output = env
    .new_float_array(v.len() as jint)
    .expect("Failed to create new float array.");

  env
    .set_float_array_region(&output, 0, &v)
    .expect("Failed to set float array.");

  output.into_raw()
}
