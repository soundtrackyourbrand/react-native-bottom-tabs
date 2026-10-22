#ifdef __cplusplus

#pragma once

#include <react/renderer/core/StateData.h>
#ifdef RN_SERIALIZABLE_STATE
#include <folly/dynamic.h>
#endif

namespace facebook::react {

class RNCTabViewState final {
 public:
  RNCTabViewState() = default;

#ifdef RN_SERIALIZABLE_STATE
  RNCTabViewState(const RNCTabViewState &previousState, folly::dynamic data)
      : imageLoader_(previousState.imageLoader_) {}

  // The state carries nothing the Java side reads.
  folly::dynamic getDynamic() const {
    return folly::dynamic::object();
  }
#endif

  void setImageLoader(std::weak_ptr<void> imageLoader);
  std::weak_ptr<void> getImageLoader() const noexcept;

 private:
  std::weak_ptr<void> imageLoader_;
};

} // namespace facebook::react

#endif
