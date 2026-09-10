document.addEventListener('DOMContentLoaded', () => {
  const canvas = document.getElementById('signature-pad');
  const ctx = canvas.getContext('2d');
  const clearButton = document.getElementById('clear-button');

  const resizeCanvasToContainer = () => {
    const rect = canvas.parentElement.getBoundingClientRect();
    canvas.width = rect.width;
    canvas.height = 200;
  };

  resizeCanvasToContainer();
  window.addEventListener('resize', resizeCanvasToContainer);

  // 기본 설정
  ctx.strokeStyle = '#000';
  ctx.lineWidth = 2;
  let isDrawing = false;

  const getOffset = (e) => {
    if (e.touches) {
      const rect = canvas.getBoundingClientRect();
      return {
        x: e.touches[0].clientX - rect.left,
        y: e.touches[0].clientY - rect.top
      };
    } else {
      return { x: e.offsetX, y: e.offsetY };
    }
  };

  // 마우스 + 터치 공통 이벤트 핸들러
  const startDrawing = (e) => {
    e.preventDefault();
    isDrawing = true;
    const { x, y } = getOffset(e);
    ctx.beginPath();
    ctx.moveTo(x, y);
  };

  const draw = (e) => {
    if (!isDrawing) return;
    e.preventDefault();
    const { x, y } = getOffset(e);
    ctx.lineTo(x, y);
    ctx.stroke();
  };

  const endDrawing = () => {
    isDrawing = false;
  };

  // 마우스 이벤트
  canvas.addEventListener('mousedown', startDrawing);
  canvas.addEventListener('mousemove', draw);
  canvas.addEventListener('mouseup', endDrawing);
  canvas.addEventListener('mouseleave', endDrawing);

  // 터치 이벤트
  canvas.addEventListener('touchstart', startDrawing);
  canvas.addEventListener('touchmove', draw);
  canvas.addEventListener('touchend', endDrawing);
  canvas.addEventListener('touchcancel', endDrawing);

  // 지우기
  clearButton.addEventListener('click', () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
  });
});